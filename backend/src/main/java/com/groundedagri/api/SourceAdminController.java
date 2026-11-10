package com.groundedagri.api;

import com.groundedagri.knowledge.ManagedSourceDocument;
import com.groundedagri.knowledge.SourceImportPreview;
import com.groundedagri.knowledge.SourceLibraryManagementService;
import com.groundedagri.knowledge.SourcePreviewUrlRequest;
import com.groundedagri.knowledge.SourceUpsertRequest;
import com.groundedagri.security.SourceAdminAuthorization;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/sources")
public class SourceAdminController {
    private static final String TOKEN_HEADER = "X-Source-Admin-Token";

    private final SourceAdminAuthorization authorization;
    private final SourceLibraryManagementService sourceService;

    public SourceAdminController(SourceAdminAuthorization authorization,
                                 SourceLibraryManagementService sourceService) {
        this.authorization = authorization;
        this.sourceService = sourceService;
    }

    @GetMapping
    public List<ManagedSourceDocument> list(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        authorization.requireToken(token);
        return sourceService.list();
    }

    @PostMapping(path = "/preview-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SourceImportPreview previewPdf(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                          @RequestPart("file") MultipartFile file) throws IOException {
        authorization.requireToken(token);
        return sourceService.previewPdf(file.getBytes(), file.getOriginalFilename());
    }

    @PostMapping("/preview-url")
    public SourceImportPreview previewUrl(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                          @Valid @RequestBody SourcePreviewUrlRequest request) {
        authorization.requireToken(token);
        return sourceService.previewUrl(request.url());
    }

    @PostMapping
    public ManagedSourceDocument create(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                        @Valid @RequestBody SourceUpsertRequest request) {
        authorization.requireToken(token);
        return sourceService.create(request);
    }

    @PutMapping("/{id}")
    public ManagedSourceDocument update(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                        @PathVariable String id,
                                        @Valid @RequestBody SourceUpsertRequest request) {
        authorization.requireToken(token);
        return sourceService.update(id, request);
    }

    @PostMapping("/{id}/reindex")
    public ManagedSourceDocument reindex(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                         @PathVariable String id) {
        authorization.requireToken(token);
        return sourceService.reindex(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                       @PathVariable String id) {
        authorization.requireToken(token);
        sourceService.delete(id);
    }
}
