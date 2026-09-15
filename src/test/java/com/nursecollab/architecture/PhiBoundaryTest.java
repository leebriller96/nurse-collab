package com.nursecollab.architecture;

import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.encounter.entity.EncounterStatus;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.phi.port.WorkRelationPort;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.global.config.AppBoundary;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원내(진료) 코드와 업무 코드가 서로를 직접 부르지 않는지 지킨다.
 *
 * 두 쪽은 다른 서버, 다른 DB 에서 돈다. 한 프로세스로 띄우는 동안에는 원내 서비스에
 * 업무 쪽 저장소를 주입해도 멀쩡히 돈다. 그리고 갈라 띄우는 날 그 자리가 전부 부서진다.
 *
 * 원내가 업무 쪽에 묻는 것은 {@link WorkRelationPort} 하나뿐이다.
 *
 * <p>무엇이 어느 쪽인지는 {@link AppBoundary} 에서 읽는다. 역할별 기동이 쓰는 표와
 * 같은 표다. 이 테스트가 따로 목록을 들고 있으면, 한쪽만 고쳐진 날 테스트는 초록인데
 * 원내에는 업무 쪽 빈이 올라간다.
 *
 * <p>서로 공유해도 되는 것은 <b>값의 이름</b>뿐이다 — 역할, 부서 유형, 주의사항 유형,
 * 재원 상태. 토큰과 업무 항목의 확인 목록이 이 이름들을 문자열로 주고받는다.
 */
class PhiBoundaryTest {

    private static final String[] PHI = AppBoundary.archPatterns(AppBoundary.PHI_PACKAGES);
    private static final String[] WORK = AppBoundary.archPatterns(AppBoundary.WORK_PACKAGES);

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

    @Test
    void 모든_도메인_패키지는_어느_한쪽에_속한다() {
        // 분류되지 않은 코드는 역할 필터를 그대로 통과해 원내와 업무 양쪽에 올라간다.
        // 새 도메인을 만들고 표에 넣는 것을 잊으면 여기서 막힌다.
        List<String> unclassified = CLASSES.stream()
                .map(JavaClass::getName)
                .filter(name -> name.startsWith("com.nursecollab.domain."))
                .filter(name -> AppBoundary.isPhi(name) == AppBoundary.isWork(name))
                .toList();

        assertThat(unclassified)
                .as("AppBoundary 에 넣지 않았거나 양쪽에 넣은 도메인 코드")
                .isEmpty();
    }
}
