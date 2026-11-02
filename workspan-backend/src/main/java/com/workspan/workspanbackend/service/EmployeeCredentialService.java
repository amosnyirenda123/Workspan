package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.CredentialBatchResponse;
import com.workspan.workspanbackend.dto.CredentialDeliveryResult;
import com.workspan.workspanbackend.repository.HumanResourceRepository;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class EmployeeCredentialService {
    private final HumanResourceRepository employees;
    private final EmployeeCredentialSender sender;

    public EmployeeCredentialService(HumanResourceRepository employees, EmployeeCredentialSender sender) {
        this.employees = employees;
        this.sender = sender;
    }

    public CredentialDeliveryResult sendOne(UUID organizationId, UUID employeeId) {
        try {
            return sender.send(organizationId, employeeId, true);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (MailException exception) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery failed. Check the SMTP settings and retry.");
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CredentialBatchResponse sendAll(UUID organizationId) {
        List<CredentialDeliveryResult> results = employees.findAllBySpaceOrganizationIdOrderByNameAsc(organizationId)
                .stream().map(employee -> {
                    try {
                        return sender.send(organizationId, employee.getId(), false);
                    } catch (ResponseStatusException exception) {
                        return new CredentialDeliveryResult(employee.getId(), java.util.Objects.toString(employee.getEmail(), ""), "FAILED",
                                exception.getReason() == null ? "Could not send credentials" : exception.getReason(), null);
                    } catch (MailException exception) {
                        return new CredentialDeliveryResult(employee.getId(), java.util.Objects.toString(employee.getEmail(), ""), "FAILED",
                                "Email delivery failed. Check SMTP settings.", null);
                    } catch (RuntimeException exception) {
                        return new CredentialDeliveryResult(employee.getId(), java.util.Objects.toString(employee.getEmail(), ""), "FAILED",
                                "Could not send credentials. Retry this employee.", null);
                    }
                }).toList();
        int sent = (int) results.stream().filter(result -> result.status().equals("SENT")).count();
        int skipped = (int) results.stream().filter(result -> result.status().equals("SKIPPED")).count();
        return new CredentialBatchResponse(sent, skipped, results.size() - sent - skipped, results);
    }
}
