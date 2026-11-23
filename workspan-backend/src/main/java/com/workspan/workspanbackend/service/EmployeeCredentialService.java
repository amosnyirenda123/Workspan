package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.exception.ServiceUnavailableException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.exception.ApiException;
import com.workspan.workspanbackend.dto.CredentialBatchResponse;
import com.workspan.workspanbackend.dto.CredentialDeliveryResult;
import com.workspan.workspanbackend.entity.RoleScope;
import com.workspan.workspanbackend.repository.HumanResourceRepository;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
public class EmployeeCredentialService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmployeeCredentialService.class);
    private final HumanResourceRepository employees;
    private final EmployeeCredentialSender sender;

    public EmployeeCredentialService(HumanResourceRepository employees, EmployeeCredentialSender sender) {
        this.employees = employees;
        this.sender = sender;
    }

    public CredentialDeliveryResult sendOne(UUID organizationId, UUID employeeId) {
        try {
            return sender.send(organizationId, employeeId, true);
        } catch (MailException exception) {
            throw new ServiceUnavailableException(ErrorCode.EMAIL_DELIVERY_FAILED, exception);
        }
    }

    public CredentialDeliveryResult invite(UUID organizationId, UUID employeeId, UUID roleId, RoleScope roleScope) {
        try {
            return sender.send(organizationId, employeeId, false, roleId, roleScope);
        } catch (MailException exception) {
            throw new ServiceUnavailableException(ErrorCode.EMAIL_DELIVERY_FAILED, exception);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CredentialBatchResponse sendAll(UUID organizationId) {
        List<CredentialDeliveryResult> results = employees.findAllBySpaceOrganizationIdOrderByNameAsc(organizationId)
                .stream().map(employee -> {
                    try {
                        return sender.send(organizationId, employee.getId(), false);
                    } catch (ApiException exception) {
                        return CredentialDeliveryResult.failed(employee.getId(), employee.getEmail(), exception.getCode(), exception.getParams());
                    } catch (MailException exception) {
                        log.warn("Credential email to employee {} failed: {}", employee.getId(), exception.getMessage());
                        return CredentialDeliveryResult.failed(employee.getId(), employee.getEmail(), ErrorCode.EMAIL_DELIVERY_FAILED, Map.of());
                    } catch (RuntimeException exception) {
                        log.error("Could not send credentials to employee {}", employee.getId(), exception);
                        return CredentialDeliveryResult.failed(employee.getId(), employee.getEmail(), ErrorCode.CREDENTIAL_DELIVERY_FAILED, Map.of());
                    }
                }).toList();
        int sent = (int) results.stream().filter(result -> result.status().equals("SENT")).count();
        int skipped = (int) results.stream().filter(result -> result.status().equals("SKIPPED")).count();
        return new CredentialBatchResponse(sent, skipped, results.size() - sent - skipped, results);
    }
}
