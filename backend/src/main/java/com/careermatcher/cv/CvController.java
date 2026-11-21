package com.careermatcher.cv;

import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.security.AuthenticatedUser;
import com.careermatcher.cv.dto.AnalysisDetail;
import com.careermatcher.cv.dto.AnalysisSummary;
import com.careermatcher.cv.dto.ApplySkillsRequest;
import com.careermatcher.cv.dto.CvDocumentResponse;
import com.careermatcher.skill.dto.CandidateSkillResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me")
@Validated
@Tag(name = "CV", description = "CV upload, AI analysis and analysis history")
@SecurityRequirement(name = "bearerAuth")
public class CvController {

  private final CvService service;

  public CvController(CvService service) {
    this.service = service;
  }

  @Operation(
      summary = "Upload a CV (PDF or DOCX) and start analysis",
      description = "Returns 202 with the pending analysis; poll GET /me/cv-analyses/{id}.")
  @PostMapping(path = "/cvs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AnalysisSummary> upload(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestPart("file") MultipartFile file) {
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_FAILED", "The upload could not be read.");
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(service.upload(user.id(), file.getOriginalFilename(), content));
  }

  @Operation(summary = "List my CVs with their analysis history")
  @GetMapping("/cvs")
  public List<CvDocumentResponse> listCvs(@AuthenticationPrincipal AuthenticatedUser user) {
    return service.listDocuments(user.id());
  }

  @Operation(summary = "Download my original CV file")
  @GetMapping("/cvs/{documentId}/file")
  public ResponseEntity<byte[]> download(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID documentId) {
    CvService.DownloadedFile file = service.download(user.id(), documentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.mediaType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .body(file.content());
  }

  @Operation(summary = "Run a new analysis on an existing CV (history is kept)")
  @PostMapping("/cvs/{documentId}/analyses")
  public ResponseEntity<AnalysisSummary> reanalyze(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID documentId) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.reanalyze(user.id(), documentId));
  }

  @Operation(summary = "Delete a CV, its stored file and its analyses")
  @DeleteMapping("/cvs/{documentId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID documentId) {
    service.deleteDocument(user.id(), documentId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "List my analyses (newest first)")
  @GetMapping("/cv-analyses")
  public Page<AnalysisSummary> listAnalyses(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.listAnalyses(user.id(), page, size);
  }

  @Operation(summary = "Get an analysis with its extracted, evidence-backed data")
  @GetMapping("/cv-analyses/{analysisId}")
  public AnalysisDetail getAnalysis(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID analysisId) {
    return service.getAnalysis(user.id(), analysisId);
  }

  @Operation(summary = "Confirm recognized skills from an analysis into my profile")
  @PostMapping("/cv-analyses/{analysisId}/apply")
  public List<CandidateSkillResponse> apply(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID analysisId,
      @Valid @RequestBody ApplySkillsRequest request) {
    return service.applySkills(user.id(), analysisId, request.skillIds());
  }
}
