package com.nursecollab.domain.phi.repository;

import com.nursecollab.domain.phi.entity.RequestMessageBody;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RequestMessageBodyRepository extends JpaRepository<RequestMessageBody, UUID> {
}
