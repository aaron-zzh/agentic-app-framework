package com.xuejiai.aaf.module.ai.aigc.project.service;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;

/** 根据 adopted 指针与有效 candidate 投影对象成熟度。 */
final class AigcProjectObjectMaturityProjector {

    private AigcProjectObjectMaturityProjector() {}

    static String project(AigcProjectObject object, boolean candidateAvailable) {
        if (object.getAdoptedVersionId() != null) {
            return "ADOPTED";
        }
        return candidateAvailable ? "CANDIDATE_AVAILABLE" : "EMPTY";
    }
}
