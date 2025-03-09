package com.egov.tendering.bidding.service.impl;

import com.egov.tendering.bidding.dal.dto.*;
import com.egov.tendering.bidding.dal.mapper.*;
import com.egov.tendering.bidding.dal.model.*;
import com.egov.tendering.bidding.dal.repository.*;
import com.egov.tendering.bidding.event.BidEventPublisher;
import com.egov.tendering.bidding.exception.*;
import com.egov.tendering.bidding.service.BidService;
import com.egov.tendering.bidding.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BidServiceImpl implements BidService {

    private final BidRepository bidRepository;
    private final BidItemRepository bidItemRepository;
    private final BidDocumentRepository bidDocumentRepository;
    private final BidSecurityRepository bidSecurityRepository;
    private final BidComplianceRepository bidComplianceRepository;
    private final ComplianceRequirementRepository complianceRequirementRepository;
    private final BidClarificationRepository bidClarificationRepository;
    private final BidVersionRepository bidVersionRepository;

    private final BidMapper bidMapper;
    private final BidItemMapper bidItemMapper;
    private final BidDocumentMapper bidDocumentMapper;
    private final BidSecurityMapper bidSecurityMapper;
    private final BidClarificationMapper clarificationMapper;
    private final BidComplianceItemMapper complianceItemMapper;
    private final BidVersionMapper bidVersionMapper;

    private final BidEventPublisher eventPublisher;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BidDTO createBid(BidSubmissionRequest request, Long tendererId) {
        log.info("Creating bid for tender ID: {} by tenderer ID: {}", request.getTenderId(), tendererId);
        Bid bid = Bid.builder()
                .tenderId(request.getTenderId())
                .tendererId(tendererId)
                .status(BidStatus.DRAFT)
                .build();

        BigDecimal totalPrice = BigDecimal.ZERO;
        List<BidItem> bidItems = new ArrayList<>();
        for (BidItemRequest itemRequest : request.getItems()) {
            BidItem bidItem = BidItem.builder()
                    .bid(bid)
                    .criteriaId(itemRequest.getCriteriaId())
                    .value(itemRequest.getValue())
                    .description(itemRequest.getDescription())
                    .build();
            totalPrice = totalPrice.add(itemRequest.getValue());
            bidItems.add(bidItem);
        }

        bid.setTotalPrice(totalPrice);
        bid.setItems(bidItems);
        bid = bidRepository.save(bid);

        eventPublisher.publishBidCreatedEvent(bid);
        return bidMapper.toDto(bid);
    }

    @Override
    @Transactional(readOnly = true)
    public BidDTO getBidById(Long bidId) {
        log.info("Getting bid by ID: {}", bidId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));
        return bidMapper.toDto(bid);
    }

    @Override
    @Transactional
    public BidDTO submitBid(Long bidId) {
        log.info("Submitting bid with ID: {}", bidId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        if (bid.getStatus() != BidStatus.DRAFT) {
            throw new InvalidBidStateException("Only draft bids can be submitted");
        }

        bid.setStatus(BidStatus.SUBMITTED);
        bid.setSubmissionTime(LocalDateTime.now());
        bid = bidRepository.save(bid);

        eventPublisher.publishBidSubmittedEvent(bid);
        return bidMapper.toDto(bid);
    }

    @Override
    @Transactional
    public BidDTO updateBidStatus(Long bidId, BidStatus status) {
        log.info("Updating bid status to {} for bid ID: {}", status, bidId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        BidStatus oldStatus = bid.getStatus();
        bid.setStatus(status);
        bid = bidRepository.save(bid);

        eventPublisher.publishBidStatusChangedEvent(bid, oldStatus);
        return bidMapper.toDto(bid);
    }

    @Override
    @Transactional
    public void deleteBid(Long bidId) {
        log.info("Deleting bid with ID: {}", bidId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        if (bid.getStatus() != BidStatus.DRAFT) {
            throw new InvalidBidStateException("Only draft bids can be deleted");
        }

        eventPublisher.publishBidDeletedEvent(bid);
        bidRepository.delete(bid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BidDTO> getBidsByTenderer(Long tendererId, Pageable pageable) {
        log.info("Getting bids for tenderer ID: {}", tendererId);
        return bidRepository.findByTendererId(tendererId, pageable)
                .map(bidMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BidDTO> getBidsByTender(Long tenderId, Pageable pageable) {
        log.info("Getting bids for tender ID: {}", tenderId);
        return bidRepository.findByTenderId(tenderId, pageable)
                .map(bidMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BidDTO> getBidsByTenderAndStatus(Long tenderId, BidStatus status) {
        log.info("Getting bids for tender ID: {} with status: {}", tenderId, status);
        return bidRepository.findByTenderIdAndStatus(tenderId, status)
                .stream()
                .map(bidMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public BidDTO addDocumentToBid(Long bidId, MultipartFile file, String fileName) {
        log.info("Adding document to bid ID: {}", bidId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        if (bid.getStatus() != BidStatus.DRAFT) {
            throw new InvalidBidStateException("Documents can only be added to draft bids");
        }

        String filePath = fileStorageService.storeFile(file, "bid-" + bidId + "-" + System.currentTimeMillis());
        BidDocument document = BidDocument.builder()
                .bid(bid)
                .name(fileName)
                .filePath(filePath)
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .uploadedBy(bid.getTendererId())
                .build();

        bidDocumentRepository.save(document);
        if (bid.getDocuments() == null) {
            bid.setDocuments(new ArrayList<>());
        }
        bid.getDocuments().add(document);

        return bidMapper.toDto(bid);
    }

    @Override
    @Transactional
    public void removeDocumentFromBid(Long bidId, Long documentId) {
        log.info("Removing document ID: {} from bid ID: {}", documentId, bidId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        if (bid.getStatus() != BidStatus.DRAFT) {
            throw new InvalidBidStateException("Documents can only be removed from draft bids");
        }

        BidDocument document = bidDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        if (!document.getBid().getId().equals(bidId)) {
            throw new IllegalArgumentException("Document does not belong to this bid");
        }

        bid.getDocuments().remove(document);
        fileStorageService.deleteFile(document.getFilePath());
        bidDocumentRepository.delete(document);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasTendererBidForTender(Long tenderId, Long tendererId) {
        return bidRepository.existsByTenderIdAndTendererId(tenderId, tendererId);
    }

    @Override
    @Transactional
    public BidDTO updateBid(Long bidId, BidSubmissionRequest request, Long tendererId) {
        log.info("Updating bid ID: {} by tenderer ID: {}", bidId, tendererId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        if (bid.getStatus() != BidStatus.DRAFT) {
            throw new InvalidBidStateException("Only draft bids can be updated");
        }

        if (!bid.getTendererId().equals(tendererId)) {
            throw new com.egov.tendering.bidding.exception.AccessDeniedException(
                    "You don't have permission to update this bid");
        }

        saveVersionData(bid, "Bid update");

        bidItemRepository.deleteAll(bid.getItems());
        BigDecimal totalPrice = BigDecimal.ZERO;
        List<BidItem> newItems = new ArrayList<>();
        for (BidItemRequest itemRequest : request.getItems()) {
            BidItem bidItem = BidItem.builder()
                    .bid(bid)
                    .criteriaId(itemRequest.getCriteriaId())
                    .value(itemRequest.getValue())
                    .description(itemRequest.getDescription())
                    .build();
            totalPrice = totalPrice.add(itemRequest.getValue());
            newItems.add(bidItem);
        }
        bid.setItems(newItems);
        bid.setTotalPrice(totalPrice);
        bidItemRepository.saveAll(newItems);

        bid = bidRepository.save(bid);
        return bidMapper.toDto(bid);
    }

    private void saveVersionData(Bid bid, String changeSummary) {
        try {
            Integer currentVersion = bidVersionRepository.findMaxVersionByBidId(bid.getId()).orElse(0);
            BidVersion version = BidVersion.builder()
                    .bidId(bid.getId())
                    .versionNumber(currentVersion + 1)
                    .versionData(objectMapper.writeValueAsString(bidMapper.toDto(bid)))
                    .changeSummary(changeSummary)
                    .createdBy(bid.getTendererId())
                    .build();
            bidVersionRepository.save(version);
        } catch (Exception e) {
            log.error("Failed to save bid version for bid ID: {}", bid.getId(), e);
            throw new RuntimeException("Failed to save bid version", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BidVersionDTO> getBidVersions(Long bidId) {
        log.info("Getting version history for bid ID: {}", bidId);
        bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId)); // Validate bid exists
        List<BidVersion> versions = bidVersionRepository.findByBidIdOrderByVersionNumberDesc(bidId);
        return versions.stream()
                .map(bidVersionMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BidVersionDTO getBidVersion(Long bidId, Integer versionNumber) {
        log.info("Getting version {} for bid ID: {}", versionNumber, bidId);
        bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId)); // Validate bid exists
        BidVersion version = bidVersionRepository.findByBidIdAndVersionNumber(bidId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version", "number", versionNumber));
        return bidVersionMapper.toDto(version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BidClarificationDTO> getClarificationsByBidId(Long bidId) {
        log.info("Fetching clarifications for bid ID: {}", bidId);
        bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId)); // Validate bid exists
        List<BidClarification> clarifications = bidClarificationRepository.findByBidId(bidId);
        return clarificationMapper.toDtoList(clarifications);
    }

    @Override
    @Transactional
    public BidClarificationDTO requestClarification(Long bidId, String question, Long evaluatorId, int daysToRespond) {
        log.info("Requesting clarification for bid ID: {} by event ID: {}", bidId, evaluatorId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        if (bid.getStatus() != BidStatus.SUBMITTED && bid.getStatus() != BidStatus.UNDER_EVALUATION) {
            throw new InvalidBidStateException("Clarifications can only be requested for submitted or under evaluation bids");
        }

        BidClarification clarification = BidClarification.builder()
                .bid(bid)
                .question(question)
                .requestedBy(evaluatorId)
                .status(ClarificationStatus.PENDING)
                .deadline(LocalDateTime.now().plusDays(daysToRespond))
                .build();

        clarification = bidClarificationRepository.save(clarification);
        eventPublisher.publishClarificationRequestedEvent(bid, clarification);
        return clarificationMapper.toDto(clarification);
    }

    @Override
    @Transactional
    public BidClarificationDTO respondToClarification(Long clarificationId, String response, Long tendererId) {
        log.info("Responding to clarification ID: {} by tenderer ID: {}", clarificationId, tendererId);
        BidClarification clarification = bidClarificationRepository.findById(clarificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Clarification", "id", clarificationId));

        if (!clarification.getBid().getTendererId().equals(tendererId)) {
            throw new com.egov.tendering.bidding.exception.AccessDeniedException(
                    "You don't have permission to respond to this clarification");
        }

        if (clarification.getStatus() != ClarificationStatus.PENDING) {
            throw new InvalidBidStateException("Can only respond to PENDING clarifications");
        }

        if (LocalDateTime.now().isAfter(clarification.getDeadline())) {
            clarification.setStatus(ClarificationStatus.EXPIRED);
            bidClarificationRepository.save(clarification);
            throw new InvalidBidStateException("The deadline for this clarification has passed");
        }

        clarification.setResponse(response);
        clarification.setRespondedAt(LocalDateTime.now());
        clarification.setStatus(ClarificationStatus.RESPONDED);
        clarification = bidClarificationRepository.save(clarification);

        eventPublisher.publishClarificationRespondedEvent(clarification);
        return clarificationMapper.toDto(clarification);
    }

    @Override
    @Transactional(readOnly = true)
    public BidSecurityDTO getBidSecurityByBidId(Long bidId) {
        log.info("Fetching bid security for bid ID: {}", bidId);
        bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId)); // Validate bid exists
        BidSecurity security = bidSecurityRepository.findByBidId(bidId)
                .orElseThrow(() -> new ResourceNotFoundException("BidSecurity", "bidId", bidId));
        return bidSecurityMapper.toDto(security);
    }

    @Override
    @Transactional
    public BidDTO submitBidWithSecurity(Long bidId, BidSecurityRequest securityRequest, MultipartFile securityDocument) {
        log.info("Submitting bid with security for bid ID: {}", bidId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        if (bid.getStatus() != BidStatus.DRAFT) {
            throw new InvalidBidStateException("Only draft bids can be submitted");
        }

        BidSecurity security = BidSecurity.builder()
                .bid(bid)
                .type(securityRequest.getType())
                .amount(securityRequest.getAmount())
                .issuerName(securityRequest.getIssuerName())
                .referenceNumber(securityRequest.getReferenceNumber())
                .issueDate(securityRequest.getIssueDate())
                .expiryDate(securityRequest.getExpiryDate())
                .build();

        if (securityDocument != null && !securityDocument.isEmpty()) {
            String documentPath = fileStorageService.storeFile(
                    securityDocument,
                    "security-" + bid.getId() + "-" + System.currentTimeMillis());
            security.setDocumentPath(documentPath);
        }

        bidSecurityRepository.save(security);
        bid.setStatus(BidStatus.SUBMITTED);
        bid.setSubmissionTime(LocalDateTime.now());
        bid = bidRepository.save(bid);

        eventPublisher.publishBidSubmittedEvent(bid);
        return bidMapper.toDto(bid);
    }

    @Override
    @Transactional
    public ComplianceCheckResult validateBidCompliance(Long bidId) {
        log.info("Validating compliance for bid ID: {}", bidId);
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));

        if (bid.getStatus() != BidStatus.SUBMITTED) {
            throw new InvalidBidStateException("Compliance can only be validated for submitted bids");
        }

        List<ComplianceRequirement> requirements = complianceRequirementRepository.findByTenderId(bid.getTenderId());
        List<BidComplianceItem> complianceItems = new ArrayList<>();
        boolean allMandatoryCompliant = true;

        for (ComplianceRequirement req : requirements) {
            boolean isCompliant = checkCompliance(bid, req);
            BidComplianceItem item = BidComplianceItem.builder()
                    .bid(bid)
                    .requirementId(req.getId())
                    .requirement(req.getDescription())
                    .mandatory(req.getMandatory())
                    .compliant(isCompliant)
                    .build();
            complianceItems.add(item);
            if (req.getMandatory() && !isCompliant) {
                allMandatoryCompliant = false;
            }
        }

        bidComplianceRepository.saveAll(complianceItems);
        List<BidComplianceItemDTO> itemDtos = complianceItemMapper.toDtoList(complianceItems);
        return new ComplianceCheckResult(allMandatoryCompliant, itemDtos);
    }

    private boolean checkCompliance(Bid bid, ComplianceRequirement requirement) {
        switch (requirement.getType()) {
            case DOCUMENT:
                return bid.getDocuments() != null && bid.getDocuments().stream()
                        .anyMatch(doc -> doc.getName().contains(requirement.getKeyword()));
            case BID_ITEM:
                return bid.getItems() != null && bid.getItems().stream()
                        .anyMatch(item -> item.getCriteriaId().equals(requirement.getCriteriaId()));
            case BID_SECURITY:
                return bidSecurityRepository.existsByBidId(bid.getId());
            case QUALIFICATION:
                return true; // Placeholder
            case OTHER:
                return true; // Placeholder
            default:
                return false;
        }
    }
}