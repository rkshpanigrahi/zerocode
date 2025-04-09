package org.jsmart.zerocode.core.reportsupload;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jsmart.zerocode.core.constants.ZeroCodeReportConstants;
import org.jsmart.zerocode.core.report.ZeroCodeReportGeneratorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class ReportUploaderImpl implements ReportUploader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZeroCodeReportGeneratorImpl.class);

    @Inject(optional = true)
    @Named("reports.repo")
    private String reportsRepo;

    @Inject(optional = true)
    @Named("reports.repo.username")
    private String reportsRepoUsername;

    @Inject(optional = true)
    @Named("reports.repo.token")
    private String reportsRepoToken;

    @Inject(optional = true)
    @Named("reports.repo.max.upload.limit.mb")
    private Integer reportsRepoMaxUploadLimitMb;

    public void uploadReport() {
        if (!isAllRequiredVariablesSet()) {
            LOGGER.warn("One or more required variables are not set. Skipping report upload.");
            return;
        }

        setDefaultUploadLimit();
        File repoDir = new File(ZeroCodeReportConstants.REPORT_UPLOAD_DIR, ".git");
        createParentDirectoryIfNotExists(repoDir);

        try (Git git = initializeOrOpenGitRepository(repoDir)) {
            addRemoteRepositoryIfMissing(git);
            addAndCommitChanges(git);
            pushToRemoteRepository(git);
        } catch (Exception e) {
            LOGGER.warn("Report upload failed: {}", e.getMessage());
        }
    }

    private void addRemoteRepositoryIfMissing(Git git) throws URISyntaxException, GitAPIException {
        if (git.remoteList().call().isEmpty()) {
            LOGGER.debug("Adding remote repository: {}", reportsRepo);
            git.remoteAdd().setName("origin").setUri(new URIish(reportsRepo)).call();
        } else {
            LOGGER.debug("Remote repository already exists.");
        }
    }

    private void addAndCommitChanges(Git git) throws GitAPIException {
        git.add().addFilepattern(".").call();
        LOGGER.debug("Added all files to the Git index.");

        git.commit()
                .setMessage("Updated files")
                .setAuthor("test", "test@test.com")
                .call();
        LOGGER.debug("Committed changes.");
    }

    private void pushToRemoteRepository(Git git) throws GitAPIException {
        git.push()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(reportsRepoUsername, reportsRepoToken))
                .call();
        LOGGER.debug("Pushed changes to remote repository!");
    }

    private boolean isAllRequiredVariablesSet() {
        return reportsRepo != null && !reportsRepo.isEmpty() &&
                reportsRepoUsername != null && !reportsRepoUsername.isEmpty() &&
                reportsRepoToken != null && !reportsRepoToken.isEmpty();
    }

    private void setDefaultUploadLimit() {
        if (reportsRepoMaxUploadLimitMb == null) {
            reportsRepoMaxUploadLimitMb = 2;
            LOGGER.debug("reportsRepoMaxUploadLimitMb is not set. Defaulting to 2 MB.");
        }
    }

    private void createParentDirectoryIfNotExists(File repoDir) {
        File parentDir = repoDir.getParentFile();
        if (!parentDir.exists() && parentDir.mkdirs()) {
            LOGGER.debug("Directory created: {}", parentDir.getAbsolutePath());
        } else if (!parentDir.exists()) {
            LOGGER.warn("Failed to create directory: {}", parentDir.getAbsolutePath());
        }
    }

    private Git initializeOrOpenGitRepository(File repoDir) throws IOException, GitAPIException {
        if (repoDir.exists()) {
            LOGGER.debug("Existing Git repository found.");
            return Git.open(new File(ZeroCodeReportConstants.REPORT_UPLOAD_DIR));
        } else {
            LOGGER.debug("Initializing a new Git repository...");
            return Git.cloneRepository()
                    .setURI(reportsRepo)
                    .setDirectory(repoDir)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(reportsRepoUsername, reportsRepoToken))
                    .call();
        }
    }


}
