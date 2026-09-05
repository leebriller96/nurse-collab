package com.nursecollab.domain.patient.repository;

import com.nursecollab.domain.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPatientNo(String patientNo);
}
