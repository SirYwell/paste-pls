package de.sirywell.pastepls.paste;

import java.util.concurrent.CompletableFuture;

public interface PasteService {
    CompletableFuture<PasteResult> upload(PasteUpload upload);
}
