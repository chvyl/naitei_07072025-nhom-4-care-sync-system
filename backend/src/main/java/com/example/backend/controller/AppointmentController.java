package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final MessageSource messageSource;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentSummaryResponse>>> getMyAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long doctorId, Pageable pageable) {

        Page<AppointmentSummaryResponse> appointmentsPage = appointmentService
                .getMyAppointments(status, startDate, endDate, doctorId, pageable);

        PageResponse<AppointmentSummaryResponse> pageResponse = PageResponse.of(appointmentsPage);

        String successMessage = messageSource.getMessage("success.appointments.retrieved", null,
                LocaleContextHolder.getLocale());

        return ResponseEntity.ok(ApiResponse.success(pageResponse, successMessage));
    }

    @PostMapping
    @Operation(summary = "Create appointment from an AVAILABLE slot")
    public ApiResponse<AppointmentCreateResponse> create(
            @Valid @RequestBody AppointmentCreateRequest request) {
        log.info("Create appointment: {}", request);
        var resp = appointmentService.create(request);
        String message = messageSource.getMessage("success.appointment.created", null,
                LocaleContextHolder.getLocale());
        return ApiResponse.success(resp, message);
    }
}
