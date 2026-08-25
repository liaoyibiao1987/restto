package com.rustto.manager.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OperLogAspect#maskJson} 单测：敏感字段递归脱敏。
 */
class OperLogAspectMaskTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Set<String> excludes() {
        Set<String> s = new HashSet<>();
        s.add("password");
        s.add("newPassword");
        return s;
    }

    @Test
    void masksTopLevelSensitiveField() throws Exception {
        String json = "{\"username\":\"admin\",\"password\":\"secret\"}";
        String out = OperLogAspect.maskJson(json, excludes(), mapper);
        JsonNode node = mapper.readTree(out);
        assertEquals("admin", node.get("username").asText());
        assertEquals("***", node.get("password").asText());
    }

    @Test
    void masksNestedSensitiveField() throws Exception {
        String json = "{\"item\":{\"password\":\"nested\"},\"list\":[{\"newPassword\":\"p\"}]}";
        String out = OperLogAspect.maskJson(json, excludes(), mapper);
        JsonNode node = mapper.readTree(out);
        assertEquals("***", node.get("item").get("password").asText());
        assertEquals("***", node.get("list").get(0).get("newPassword").asText());
    }

    @Test
    void noExcludesReturnsOriginal() {
        String json = "{\"password\":\"secret\"}";
        assertEquals(json, OperLogAspect.maskJson(json, Collections.emptySet(), mapper));
    }

    @Test
    void invalidJsonReturnsOriginal() {
        String json = "{not valid";
        assertEquals(json, OperLogAspect.maskJson(json, excludes(), mapper));
    }

    @Test
    void nullSafe() {
        assertTrue(OperLogAspect.maskJson(null, excludes(), mapper) == null);
        assertEquals("", OperLogAspect.maskJson("", excludes(), mapper));
    }
}
