package com.nursecollab.global.config;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/**
 * 역할에 속하지 않는 클래스를 스캔에서 뺀다.
 *
 * 컴포넌트 스캔과 저장소 스캔 양쪽에 같은 필터를 건다. 한쪽만 걸면 원내에
 * 업무 쪽 저장소가 올라가고, 원내 DB 에는 그 테이블이 없어 기동 중에 터진다 —
 * 그나마 터지면 다행이고, 테이블이 남아 있는 DB 라면 조용히 뜬다.
 *
 * 스프링이 필터를 만들 때 환경을 넣어 준다(EnvironmentAware).
 */
public class RoleTypeExcludeFilter implements TypeFilter, EnvironmentAware {

    private AppRole role;

    @Override
    public void setEnvironment(Environment environment) {
        this.role = AppRole.from(environment);
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
        if (role == null) {
            // 역할을 모르는 채로 걸러 내면 전부 통과시키게 된다. 그러면 원내에 전부 올라간다.
            throw new IllegalStateException("역할을 모르는 채로 스캔하려 했습니다. 환경이 주입되지 않았습니다.");
        }
        return !role.includes(metadataReader.getClassMetadata().getClassName());
    }
}
