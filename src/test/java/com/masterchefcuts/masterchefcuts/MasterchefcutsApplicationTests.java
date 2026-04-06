package com.masterchefcuts.masterchefcuts;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Full context requires a live database — run only in integration environment")
class MasterchefcutsApplicationTests {

	@Test
	void contextLoads() {
	}

}
