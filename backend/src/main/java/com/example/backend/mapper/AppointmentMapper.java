package com.example.backend.mapper;

import com.example.backend.dto.AppointmentSummaryResponse;
import com.example.backend.entity.Appointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(source = "appointmentSlot.doctor.user.fullName", target = "doctorName")
    @Mapping(source = "appointmentSlot.doctor.specialty.name", target = "specialtyName")
    AppointmentSummaryResponse toAppointmentSummaryResponse(Appointment appointment);
}
