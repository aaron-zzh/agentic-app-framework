package com.xuejiai.aaf.module.system.mail.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.mail.domain.MailAccount;
import com.xuejiai.aaf.module.system.mail.domain.MailLog;
import com.xuejiai.aaf.module.system.mail.domain.MailTemplate;
import com.xuejiai.aaf.module.system.mail.repository.MailAccountRepository;
import com.xuejiai.aaf.module.system.mail.repository.MailLogRepository;
import com.xuejiai.aaf.module.system.mail.repository.MailTemplateRepository;
import com.xuejiai.aaf.module.system.mail.vo.MailAccountCreateDTO;
import com.xuejiai.aaf.module.system.mail.vo.MailAccountVO;
import com.xuejiai.aaf.module.system.mail.vo.MailLogVO;
import com.xuejiai.aaf.module.system.mail.vo.MailTemplateCreateDTO;
import com.xuejiai.aaf.module.system.mail.vo.MailTemplateVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 邮件服务（账号管理 + 模板管理 + 发送 + 日志）。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final MailAccountRepository accountRepository;
    private final MailTemplateRepository templateRepository;
    private final MailLogRepository logRepository;

    // ── 账号管理 ──────────────────────────────────────────────

    /**
     * 查询所有邮件账号。
     *
     * @return 账号列表
     */
    public List<MailAccountVO> listAccounts() {
        return accountRepository.findAll().stream()
                .filter(a -> !a.getDeleted())
                .map(this::toAccountVO)
                .toList();
    }

    /**
     * 创建邮件账号。
     *
     * @param dto 创建请求
     * @return 新建账号
     */
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

    /**
     * 删除邮件账号。
     *
     * @param id 账号 ID
     */
    @Transactional
    public void deleteAccount(Long id) {
        accountRepository.deleteById(id);
    }

    // ── 模板管理 ──────────────────────────────────────────────

    /**
     * 查询所有邮件模板。
     *
     * @return 模板列表
     */
    public List<MailTemplateVO> listTemplates() {
        return templateRepository.findAll().stream()
                .filter(t -> !t.getDeleted())
                .map(this::toTemplateVO)
                .toList();
    }

    /**
     * 创建邮件模板。
     *
     * @param dto 创建请求
     * @return 新建模板
     */
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

    /**
     * 删除邮件模板。
     *
     * @param id 模板 ID
     */
    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.deleteById(id);
    }

    // ── 发送邮件 ──────────────────────────────────────────────

    /**
     * 按模板编码发送邮件。
     *
     * @param toAddress 收件人
     * @param templateCode 模板编码
     * @param params 模板变量
     */
    public void send(String toAddress, String templateCode, Map<String, String> params) {
        var template =
                templateRepository
                        .findByCodeAndDeletedFalse(templateCode)
                        .orElseThrow(
                                () -> new IllegalArgumentException("邮件模板不存在: " + templateCode));
        var account =
                accountRepository
                        .findByIdAndDeletedFalse(template.getAccountId())
                        .orElseThrow(() -> new IllegalArgumentException("邮件账号不存在"));

        // 渲染内容（简单变量替换）
        var renderedContent = renderContent(template.getContent(), params);
        var renderedSubject = renderContent(template.getSubject(), params);

        var mailLog = new MailLog();
        mailLog.setTemplateId(template.getId());
        mailLog.setToAddress(toAddress);
        mailLog.setSubject(renderedSubject);
        mailLog.setContent(renderedContent);
        mailLog.setSendTime(LocalDateTime.now());

        try {
            doSend(account, toAddress, renderedSubject, renderedContent);
            mailLog.setSendStatus((short) 1);
        } catch (Exception e) {
            mailLog.setSendStatus((short) 2);
            mailLog.setErrorMessage(e.getMessage());
            log.error("邮件发送失败: to={}, template={}", toAddress, templateCode, e);
        }
        logRepository.save(mailLog);
    }

    // ── 日志查询 ──────────────────────────────────────────────

    /**
     * 分页查询邮件日志。
     *
     * @param pageNo 页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public Page<MailLogVO> listLogs(int pageNo, int pageSize) {
        var page =
                logRepository.findAll(
                        PageRequest.of(
                                pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return page.map(this::toLogVO);
    }

    // ── 内部方法 ──────────────────────────────────────────────

    private void doSend(MailAccount account, String to, String subject, String content) {
        JavaMailSender sender = buildSender(account);
        try {
            var message = ((JavaMailSenderImpl) sender).createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(account.getFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            sender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    private JavaMailSender buildSender(MailAccount account) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost());
        sender.setPort(account.getPort());
        sender.setUsername(account.getUsername());
        sender.setPassword(account.getPassword());
        var props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (Boolean.TRUE.equals(account.getSslEnabled())) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        return sender;
    }

    private String renderContent(String template, Map<String, String> params) {
        if (params == null || template == null) return template;
        var result = template;
        for (var entry : params.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private MailAccountVO toAccountVO(MailAccount a) {
        return new MailAccountVO(
                a.getId(),
                a.getName(),
                a.getHost(),
                a.getPort(),
                a.getUsername(),
                a.getSslEnabled(),
                a.getFromAddress(),
                a.getStatus());
    }

    private MailTemplateVO toTemplateVO(MailTemplate t) {
        return new MailTemplateVO(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getSubject(),
                t.getContent(),
                t.getAccountId(),
                t.getParams(),
                t.getStatus());
    }

    private MailLogVO toLogVO(MailLog l) {
        return new MailLogVO(
                l.getId(),
                l.getTemplateId(),
                l.getToAddress(),
                l.getSubject(),
                l.getSendStatus(),
                l.getSendTime(),
                l.getErrorMessage());
    }
}
