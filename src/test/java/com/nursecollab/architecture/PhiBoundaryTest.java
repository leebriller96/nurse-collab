package com.nursecollab.architecture;

import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.encounter.entity.EncounterStatus;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.phi.port.WorkRelationPort;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 원내(진료) 코드와 업무 코드가 서로를 직접 부르지 않는지 지킨다.
 *
 * 두 쪽은 언젠가 다른 서버, 다른 DB 에서 돈다. 그 전까지는 한 프로세스라
 * 원내 서비스에 업무 쪽 저장소를 주입해도 멀쩡히 돈다. 그리고 갈라 띄우는 날
 * 그 자리가 전부 부서진다. 컴파일도 테스트도 통과하는 동안에는 아무도 모른다.
 *
 * 그래서 규칙을 코드로 박아 둔다. 원내가 업무 쪽에 묻는 것은 {@link WorkRelationPort} 하나뿐이다.
 *
 * <p>서로 공유해도 되는 것은 <b>값의 이름</b>뿐이다 — 역할, 부서 유형, 주의사항 유형,
 * 재원 상태. 토큰과 업무 항목의 확인 목록이 이 이름들을 문자열로 주고받는다.
 * 엔티티와 저장소는 공유하지 않는다.
 */
class PhiBoundaryTest {

    private static final String[] PHI = {
            "com.nursecollab.domain.phi..",
            "com.nursecollab.domain.encounter..",
            "com.nursecollab.domain.patient..",
            "com.nursecollab.domain.nursing..",
    };

    private static final String[] WORK = {
            "com.nursecollab.domain.workorder..",
            "com.nursecollab.domain.episode..",
            "com.nursecollab.domain.staff..",
            "com.nursecollab.domain.department..",
            "com.nursecollab.domain.notification..",
            "com.nursecollab.domain.stats..",
            "com.nursecollab.domain.master..",
    };

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.nursecollab");

    @Test
    void 원내_코드는_업무_쪽을_직접_부르지_않는다() {
        noClasses().that().resideInAnyPackage(PHI)
                .should().dependOnClassesThat(resideInAnyPackage(WORK)
                        // 토큰에 실려 오는 값의 이름. 엔티티가 아니다.
                        .and(not(belongToAnyOf(StaffRole.class, DeptType.class))))
                .because("원내와 업무는 다른 DB 에서 돈다. 업무 쪽에 묻는 것은 WorkRelationPort 로만 한다")
                .check(CLASSES);
    }

    @Test
    void 업무_코드는_원내_쪽을_직접_부르지_않는다() {
        noClasses().that().resideInAnyPackage(WORK)
                .should().dependOnClassesThat(resideInAnyPackage(PHI)
                        // 업무 항목의 확인 목록과 침대 상태가 쓰는 값의 이름
                        .and(not(belongToAnyOf(AlertType.class, EncounterStatus.class)))
                        // 원내의 질문에 답하는 쪽이 그 질문의 모양을 아는 것은 괜찮다
                        .and(not(belongToAnyOf(WorkRelationPort.class))))
                .because("업무 쪽이 진료정보를 읽는 순간 나눈 의미가 사라진다")
                .check(CLASSES);
    }
}
