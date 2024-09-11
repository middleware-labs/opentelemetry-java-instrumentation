/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCustomerRepository
    extends JpaRepository<JpaCustomer, Long>, JpaCustomerRepositoryCustom {
  List<JpaCustomer> findByLastName(String lastName);

  Optional<JpaCustomer> findOneByLastName(String lastName);
}
