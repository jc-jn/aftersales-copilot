package com.aftersales.copilot.auth.infrastructure;

import com.aftersales.copilot.auth.infrastructure.po.AuthRefreshTokenPo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<AuthRefreshTokenPo> {
    @Insert("""
            INSERT INTO auth_refresh_token
                (id, user_id, token_hash, expires_at, revoked_at, device_info, created_at, updated_at)
            VALUES (#{id}, #{userId}, #{tokenHash}, #{expiresAt}, NULL, #{deviceInfo}, #{now}, #{now})
            """)
    int save(@Param("id") long id, @Param("userId") long userId, @Param("tokenHash") String tokenHash,
             @Param("expiresAt") LocalDateTime expiresAt, @Param("deviceInfo") String deviceInfo, @Param("now") LocalDateTime now);

    @Select("""
            SELECT id, user_id, expires_at, revoked_at
            FROM auth_refresh_token
            WHERE token_hash = #{tokenHash}
            FOR UPDATE
            """)
    Optional<RefreshTokenRepository.StoredRefreshToken> findByHashForUpdate(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE auth_refresh_token
            SET revoked_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND revoked_at IS NULL
            """)
    int revoke(@Param("id") long id, @Param("now") LocalDateTime now);
}
