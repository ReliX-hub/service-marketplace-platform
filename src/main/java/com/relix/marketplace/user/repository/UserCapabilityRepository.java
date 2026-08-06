package com.relix.marketplace.user.repository;

import com.relix.marketplace.user.entity.UserCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface UserCapabilityRepository extends JpaRepository<UserCapability, UserCapability.Key> {

    Set<UserCapability> findByUser_Id(Long userId);
}
