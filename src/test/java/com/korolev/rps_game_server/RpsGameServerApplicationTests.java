package com.korolev.rps_game_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "rps.enabled=false")
class RpsGameServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
