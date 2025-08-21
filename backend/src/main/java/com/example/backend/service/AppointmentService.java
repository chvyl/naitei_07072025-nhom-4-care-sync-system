package com.example.backend.service;

import com.example.backend.dto.AppointmentCreateRequest;
import com.example.backend.dto.AppointmentCreateResponse;
import com.example.backend.dto.AppointmentSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface AppointmentService {

    Page<AppointmentSummaryResponse> getMyAppointments(String status, LocalDate startDate,
            LocalDate endDate, Long doctorId, Pageable pageable);

    AppointmentCreateResponse create(AppointmentCreateRequest request);
}
