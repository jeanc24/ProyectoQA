package icc354.pucmm.proyectoqa;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
@Tag("integration")
class ProyectoQaApplicationTests {

    @Test
    void contextLoads() {
    }
}