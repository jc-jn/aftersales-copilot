package com.aftersales.copilot.auth.infrastructure;

import com.aftersales.copilot.auth.domain.AuthUser;
import com.aftersales.copilot.auth.infrastructure.po.SysUserPo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface AuthUserMapper extends BaseMapper<SysUserPo> {
    @Select("""
            SELECT id, username, email, password_hash, display_name, role, status
            FROM sys_user
            WHERE deleted_at IS NULL AND (username = #{login} OR email = #{login})
            LIMIT 1
            """)
    Optional<AuthUser> findByLogin(@Param("login") String login);

    @Select("""
            SELECT id, username, email, password_hash, display_name, role, status
            FROM sys_user
            WHERE id = #{id} AND status = 'ACTIVE' AND deleted_at IS NULL
            """)
    Optional<AuthUser> findActiveById(@Param("id") long id);

    @Update("UPDATE sys_user SET last_login_at = #{now}, updated_at = #{now} WHERE id = #{id}")
    int updateLastLogin(@Param("id") long id, @Param("now") LocalDateTime now);
}
