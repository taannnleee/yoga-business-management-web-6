package org.example.yogabusinessmanagementweb.repositories;

import org.example.yogabusinessmanagementweb.common.entities.LectureProductAd;
import org.example.yogabusinessmanagementweb.common.entities.Lectures;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LecturesRepository extends JpaRepository<Lectures, Long> {
}
