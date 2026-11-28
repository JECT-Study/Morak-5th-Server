package com.morak.feature.ad.presentation.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.morak.common.config.SecurityConfig;
import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.service.ClaimAdRewardBySsvService;
import com.morak.feature.ad.application.service.ClaimAdRewardService;
import com.morak.feature.ad.application.service.StartAdRewardService;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AdSsvController.class, AdController.class},
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({SecurityConfig.class, LoginUserJwtConverter.class})
class AdSsvControllerTest {

	private static final String RAW_QUERY = "ad_unit=1&custom_data=a%2Bb&signature=sig&key_id=7";

	@Autowired private MockMvc mvc;
	@MockitoBean private ClaimAdRewardBySsvService ssv;
	@MockitoBean private ClaimAdRewardService claim;
	@MockitoBean private StartAdRewardService start;
	@MockitoBean private AuthenticateUserService users;
	@MockitoBean private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	@Test
	void googleCanCallWithoutLoginAndTheRawQueryIsPassedUndecoded() throws Exception {
		mvc.perform(get(java.net.URI.create("/ads/ssv?" + RAW_QUERY)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(ssv).claim(RAW_QUERY);
	}

	@Test
	void invalidSignatureIsAnswered400() throws Exception {
		when(ssv.claim(anyString())).thenThrow(new BusinessException(AdErrorCode.INVALID_SSV, "서명 불일치"));

		mvc.perform(get(java.net.URI.create("/ads/ssv?" + RAW_QUERY)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("AD_INVALID_SSV"));
	}

	@Test
	void otherAdEndpointsStillNeedLogin() throws Exception {
		mvc.perform(post("/ads/reward-sessions")).andExpect(status().isUnauthorized());
		mvc.perform(post("/ads/rewards").contentType("application/json").content("{\"token\":\"x\"}"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(start, claim);
	}
}
