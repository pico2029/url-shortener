package com.example.shortener.link;

import com.example.shortener.link.service.LinkService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
@AutoConfigureMockMvc
@Testcontainers
class RedirectCacheUnavailableIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired private MockMvc mvc;
    @Autowired private LinkService links;

    @Test
    void startsHealthyAndResolvesWithoutRedis() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        var link = links.create("https://example.com/without-redis");
        mvc.perform(get("/r/" + link.shortCode())).andExpect(status().isFound())
                .andExpect(header().string("Location", link.originalUrl()));
        mvc.perform(head("/r/" + link.shortCode())).andExpect(status().isFound())
                .andExpect(content().string(""));
        mvc.perform(get("/r/missing-without-redis")).andExpect(status().isNotFound());
    }
}
