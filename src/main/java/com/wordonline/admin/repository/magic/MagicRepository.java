package com.wordonline.admin.repository.magic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wordonline.admin.entity.magic.Magic;

public interface MagicRepository extends JpaRepository<Magic, Long> {

    List<Magic> findAllBy();

    List<Magic> findAllByOrderByIdAsc();

    Optional<Magic> findByName(String name);
}
