package com.morak.feature.challenge.presentation.dto.request;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.domain.entity.Challenge.Category;
import com.morak.feature.challenge.domain.entity.Challenge.Visibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateChallengeRequest(
		@NotBlank @Size(max = 12) String title,
		@NotNull Visibility visibility,
		@NotNull Category category,
		@NotBlank String purpose,
		@NotBlank String commonMission,
		@NotBlank String verificationRules,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@NotNull @Schema(type = "integer") BigDecimal capacity,
		@Schema(type = "integer") BigDecimal pointAmount,
		String expectations) {

	/** 생략된 참여 포인트는 0으로 처리하며 소수 값을 반올림하지 않는다. */
	public CreateChallengeCommand toCommand() {
		return new CreateChallengeCommand(title, visibility, category, purpose, commonMission, verificationRules,
				startDate, endDate, exactInteger(capacity, ChallengeErrorCode.INVALID_CAPACITY),
				pointAmount == null ? 0 : exactInteger(pointAmount, ChallengeErrorCode.INVALID_POINT_AMOUNT), expectations);
	}

	/** 소수 또는 int 범위를 넘는 값은 해당 필드의 요청 오류로 변환한다. */
	private static int exactInteger(BigDecimal number, ChallengeErrorCode errorCode) {
		try {
			return number.intValueExact();
		} catch (ArithmeticException exception) {
			throw new BusinessException(errorCode);
		}
	}
}
