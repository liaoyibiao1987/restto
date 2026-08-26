package com.restto.manager.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.restto.manager.entity.system.operlog.SysOperLog;
import com.restto.manager.service.system.operlog.OperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 操作日志切面：拦截 {@link OperLog} 标注的 Controller 方法，异步记录操作日志到 sys_oper_log。
 *
 * <p>记录字段：标题/操作人/URI/方法/请求方式/入参(脱敏)/状态/错误/耗时/IP/时间。
 * 落库走单线程守护线程池异步执行，任何异常仅记录日志、绝不阻断业务请求。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 错误信息最长保留字符数。 */
    private static final int MAX_ERROR_LEN = 2000;

    private final OperLogService operLogService;

    private final ObjectMapper objectMapper;

    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "oper-log-writer");
        t.setDaemon(true);
        return t;
    });

    /**
     * 环绕增强：记录方法执行结果与耗时。
     *
     * @param pjp     连接点
     * @param operLog 注解（由 {@code @annotation} 绑定）
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常原样上抛
     */
    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long startNanos = System.nanoTime();
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            record(pjp, operLog, startNanos, error);
        }
    }

    /** 组装并异步落库一条日志；全程容错。 */
    private void record(ProceedingJoinPoint pjp, OperLog ann, long startNanos, Throwable error) {
        try {
            HttpServletRequest request = currentRequest();
            UserContext.CurrentUser current = UserContext.get();

            SysOperLog entity = new SysOperLog();
            entity.setTitle(ann.value());
            if (current != null) {
                entity.setOperUser(current.username);
                entity.setOperUserId(current.userId);
            }
            if (request != null) {
                entity.setOperUri(request.getRequestURI());
                entity.setRequestMethod(request.getMethod());
                entity.setOperIp(extractIp(request));
            }
            Signature sig = pjp.getSignature();
            entity.setOperMethod(sig.getDeclaringTypeName() + "#" + sig.getName());
            entity.setRequestParams(serializeArgs(pjp.getArgs(), ann.excludeParams()));
            entity.setStatus(error == null ? 1 : 0);
            entity.setErrorMsg(error == null ? null : truncate(error.toString(), MAX_ERROR_LEN));
            entity.setCostMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            entity.setOperTime(java.time.LocalDateTime.now());

            pool.submit(() -> {
                try {
                    operLogService.save(entity);
                } catch (Exception ex) {
                    log.error("操作日志落库失败（已忽略）：{}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("操作日志记录异常（已忽略，不影响业务）：{}", e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest();
        } catch (IllegalStateException e) {
            // 非请求上下文（如异步/定时）下无 HttpServletRequest
            return null;
        }
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 序列化入参并对敏感字段递归脱敏。 */
    private String serializeArgs(Object[] args, String[] excludeParams) {
        if (args == null || args.length == 0) {
            return null;
        }
        Set<String> excludes = excludeParams == null ? new HashSet<>() : new HashSet<>(Arrays.asList(excludeParams));
        try {
            String raw = objectMapper.writeValueAsString(args);
            return maskJson(raw, excludes, objectMapper);
        } catch (Exception e) {
            log.debug("入参序列化失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 对 JSON 文本递归脱敏：键名命中 {@code excludes} 的值置为 {@code ***}（可单测）。
     *
     * @param json     原始 JSON
     * @param excludes 需脱敏的字段名集合
     * @param mapper   ObjectMapper
     * @return 脱敏后的 JSON；解析失败返回原文
     */
    static String maskJson(String json, Set<String> excludes, ObjectMapper mapper) {
        if (json == null || json.isEmpty() || excludes == null || excludes.isEmpty()) {
            return json;
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode masked = maskNode(root, excludes);
            return mapper.writeValueAsString(masked);
        } catch (Exception e) {
            return json;
        }
    }

    /** 递归遍历节点进行脱敏。 */
    private static JsonNode maskNode(JsonNode node, Set<String> excludes) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fields = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                if (excludes.contains(field)) {
                    obj.set(field, TextNode.valueOf("***"));
                } else {
                    JsonNode child = obj.get(field);
                    JsonNode masked = maskNode(child, excludes);
                    if (masked != child) {
                        obj.set(field, masked);
                    }
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                JsonNode masked = maskNode(child, excludes);
                if (masked != child) {
                    arr.set(i, masked);
                }
            }
            return arr;
        }
        return node;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
