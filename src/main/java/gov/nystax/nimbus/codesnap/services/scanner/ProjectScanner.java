package gov.nystax.nimbus.codesnap.services.scanner;

import gov.nystax.nimbus.codesnap.domain.ProjectSnap;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;

public interface ProjectScanner {

    ProjectSnap scanProject(ProjectInfo projectInfo);
}
