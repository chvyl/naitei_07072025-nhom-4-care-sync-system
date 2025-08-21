package com.example.backend.repository;

import com.example.backend.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // Import
import org.springframework.stereotype.Repository;

@Repository
// Thêm JpaSpecificationExecutor<Appointment> vào đây
public interface AppointmentRepository
        extends
            JpaRepository<Appointment, Long>,
            JpaSpecificationExecutor<Appointment> {
}
