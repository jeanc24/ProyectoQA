package icc354.pucmm.proyectoqa.domain.repository;

import icc354.pucmm.proyectoqa.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    
}