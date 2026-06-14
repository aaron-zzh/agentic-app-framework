package com.xuejiai.aaf.module.system.mail.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.mail.domain.MailAccount;
import com.xuejiai.aaf.module.system.mail.domain.MailTemplate;
import com.xuejiai.aaf.module.system.mail.repository.MailAccountRepository;
import com.xuejiai.aaf.module.system.mail.repository.MailTemplateRepository;
import com.xuejiai.aaf.module.system.mail.vo.MailAccountCreateDTO;
import com.xuejiai.aaf.module.system.mail.vo.MailAccountVO;
import com.xuejiai.aaf.module.system.mail.vo.MailTemplateCreateDTO;
import com.xuejiai.aaf.module.system.mail.vo.MailTemplateVO;

import lombok.RequiredArgsConstructor;

/**
 * 邮件服务（账号管理 + 模板管理）。
 *
 * <p>发送邮件统一通过 {@link com.xuejiai.aaf.framework.messaging.MessageService} 进行。
 * 发送日志统一记录在 sys_message_log，查询入口见 MessageLogController。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private final MailAccountRepository accountRepository;
    private final MailTemplateRepository templateRepository;

    public List<MailAccountVO> listAccounts() {
        return accountRepository.findAll().stream()
                .filter(a -> !a.getDeleted())
                .map(this::toAccountVO)
                .toList();
    }

    @Transactional
    public MailAccountVO createAccount(MailAccountCreateDTO dto) {
        var account = new MailAccount();
        account.setName(dto.name());
        account.setHost(dto.host());
        account.setPort(dto.port());
        account.setUsername(dto.username());
        account.setPassword(dto.password());
        account.setSslEnabled(dto.sslEnabled() != null ? dto.sslEnabled() : false);
        account.setFromAddress(dto.fromAddress());
        return toAccountVO(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long id) {
        accountRepository.deleteById(id);
    }

    public List<MailTemplateVO> listTemplates() {
        return templateRepository.findAll().stream()
                .filter(t -> !t.getDeleted())
                .map(this::toTemplateVO)
                .toList();
    }

    @Transactional
    public MailTemplateVO createTemplate(MailTemplateCreateDTO dto) {
        var template = new MailTemplate();
        template.setCode(dto.code());
        template.setName(dto.name());
        template.setSubject(dto.subject());
        template.setContent(dto.content());
        template.setAccountId(dto.accountId());
        template.setParams(dto.params());
        return toTemplateVO(templateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.deleteById(id);
    }

    private MailAccountVO toAccountVO(MailAccount a) {
        return new MailAccountVO(
                a.getId(), a.getName(), a.getHost(), a.getPort(),
                a.getUsername(), a.getSslEnabled(), a.getFromAddress(), a.getStatus());
    }

    private MailTemplateVO toTemplateVO(MailTemplate t) {
        return new MailTemplateVO(
                t.getId(), t.getCode(), t.getName(), t.getSubject(),
                t.getContent(), t.getAccountId(), t.getParams(), t.getStatus());
    }
}
