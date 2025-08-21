package com.example.backend.service.impl;

import com.example.backend.dto.AppointmentCreateRequest;
import com.example.backend.dto.AppointmentCreateResponse;
import com.example.backend.dto.AppointmentCreateResponse.DoctorInfo;
import com.example.backend.dto.AppointmentCreateResponse.ServiceItem;
import com.example.backend.dto.AppointmentCreateResponse.SlotInfo;
import com.example.backend.dto.AppointmentSummaryResponse;
import com.example.backend.entity.*;
import com.example.backend.entity.ids.AppointmentServiceId;
import com.example.backend.exception.BusinessException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.mapper.AppointmentMapper;
import com.example.backend.repository.*;
import com.example.backend.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.example.backend.constant.enums.AppointmentStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final AppointmentServiceRepository appointmentServiceRepository;
    private final ServiceRepository serviceRepository;
    private final PatientRepository patientRepository;
    private final AppointmentMapper appointmentMapper;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    @Override
    @Transactional
    public AppointmentCreateResponse create(AppointmentCreateRequest request) {
        log.info("Creating appointment for patient {} with slot {}", request.patientId(),
                request.slotId());

        AppointmentSlot slot = appointmentSlotRepository.findById(request.slotId()).orElseThrow(
                () -> new ResourceNotFoundException("error.slot.not.found", request.slotId()));

        if (slot.getStatus() != com.example.backend.constant.enums.AppointmentSlotStatus.AVAILABLE) {
            throw new BusinessException("error.appointment.slot.unavailable");
        }
        if (slot.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("error.appointment.slot.unavailable");
        }

        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("error.patient.not.found",
                        request.patientId()));

        List<com.example.backend.entity.Service> services = serviceRepository
                .findAllById(request.serviceIds());
        if (services.size() != request.serviceIds().size()) {
            throw new ResourceNotFoundException("error.service.not.found");
        }

        Long slotSpecialtyId = slot.getDoctor().getSpecialty() != null
                ? slot.getDoctor().getSpecialty().getId().longValue()
                : null;
        boolean allMatch = services.stream().allMatch(svc -> svc.getSpecialty() != null
                && Objects.equals(svc.getSpecialty().getId().longValue(), slotSpecialtyId));
        if (!allMatch) {
            throw new BusinessException("error.service.specialty.mismatch");
        }

        Appointment appointmentToSave = new Appointment();
        appointmentToSave.setPatient(patient);
        appointmentToSave.setNotes(request.notes());
        appointmentToSave.setStatus(com.example.backend.constant.enums.AppointmentStatus.PENDING);
        final Appointment savedAppt = appointmentRepository.save(appointmentToSave);

        int updated = appointmentSlotRepository.reserveSlot(slot.getId(), savedAppt.getId());
        if (updated == 0) {
            throw new BusinessException("error.appointment.slot.unavailable");
        }

        List<com.example.backend.entity.AppointmentService> appointmentServices = services.stream()
                .map(svc -> {
                    var as = new com.example.backend.entity.AppointmentService();
                    as.setId(new AppointmentServiceId(savedAppt.getId(), svc.getId()));
                    as.setAppointment(savedAppt);
                    as.setService(svc);
                    as.setPriceAtBooking(svc.getPrice());
                    return as;
                }).toList();

        appointmentServiceRepository.saveAll(appointmentServices);

        BigDecimal total = appointmentServices.stream()
                .map(com.example.backend.entity.AppointmentService::getPriceAtBooking)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Doctor doctor = slot.getDoctor();
        Specialty specialty = doctor.getSpecialty();
        User user = doctor.getUser();
        return new AppointmentCreateResponse(savedAppt.getId(), patient.getId(),
                new SlotInfo(slot.getStartTime(), slot.getEndTime(), doctor.getId()),
                new DoctorInfo(doctor.getId(),
                        Optional.ofNullable(user).map(User::getFullName).orElse(null),
                        Optional.ofNullable(specialty).map(s -> s.getId().longValue()).orElse(null),
                        Optional.ofNullable(specialty).map(Specialty::getName).orElse(null)),
                savedAppt.getStatus().name(),
                services.stream()
                        .map(svc -> new ServiceItem(svc.getId(), svc.getName(), svc.getPrice()))
                        .toList(),
                total, request.notes());
    }

    @Override
    public Page<AppointmentSummaryResponse> getMyAppointments(String status, LocalDate startDate,
            LocalDate endDate, Long doctorId, Pageable pageable) {
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow(() -> {
            String message = messageSource.getMessage("error.unauthorized", null, new Locale("vi"));
            return new RuntimeException(message);
        });

        Specification<Appointment> spec = isPatient(currentUser);

        if (StringUtils.hasText(status)) {
            spec = spec.and(hasStatus(status));
        }
        if (startDate != null) {
            spec = spec.and(startsAtOrAfter(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            spec = spec.and(startsBefore(endDate.plusDays(1).atStartOfDay()));
        }
        if (doctorId != null) {
            spec = spec.and(hasDoctor(doctorId));
        }

        Page<Appointment> appointments = appointmentRepository.findAll(spec, pageable);

        return appointments.map(appointmentMapper::toAppointmentSummaryResponse);
    }

    private Specification<Appointment> isPatient(User user) {
        return (root, query, cb) -> cb.equal(root.get("patient").get("user"), user);
    }

    private Specification<Appointment> hasStatus(String status) {
        AppointmentStatus st;
        try {
            st = AppointmentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), st);
    }

    private Specification<Appointment> startsAtOrAfter(LocalDateTime from) {
        return (root, query, cb) -> cb
                .greaterThanOrEqualTo(root.get("appointmentSlot").get("startTime"), from);
    }

    private Specification<Appointment> startsBefore(LocalDateTime toExclusive) {
        return (root, query, cb) -> cb.lessThan(root.get("appointmentSlot").get("startTime"),
                toExclusive);
    }

    private Specification<Appointment> hasDoctor(Long doctorId) {
        return (root, query, cb) -> cb.equal(root.get("appointmentSlot").get("doctor").get("id"),
                doctorId);
    }
}
