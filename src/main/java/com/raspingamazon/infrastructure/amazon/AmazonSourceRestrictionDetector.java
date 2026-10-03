package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.SourceRestrictionType;

import java.util.Locale;
import java.util.Objects;

/**
 * Detecta evidências explícitas conhecidas de restrição da fonte Amazon.
 *
 * <p>O detector é compartilhado entre /deals e a página individual de
 * produto. Assim as duas fronteiras utilizam exatamente os mesmos
 * critérios para CAPTCHA, challenge e bloqueio.</p>
 *
 * <p>A política é deliberadamente conservadora. Palavras isoladas como
 * "captcha" ou "robot" não são suficientes, pois poderiam aparecer em
 * scripts, textos ou outros conteúdos legítimos.</p>
 *
 * <p>Esta classe somente reconhece restrições. Ela não tenta resolver
 * CAPTCHA, executar challenges, modificar fingerprint ou contornar
 * qualquer proteção da fonte.</p>
 */
public final class AmazonSourceRestrictionDetector {

    private static final String CAPTCHA_FORM_MARKER =
        "/errors/validatecaptcha";

    private static final String CAPTCHA_INSTRUCTION_MARKER =
        "enter the characters you see below";

    private static final String CAPTCHA_IMAGE_INSTRUCTION_MARKER =
        "type the characters you see in this image";

    private static final String ROBOT_CHALLENGE_MARKER =
        "sorry, we just need to make sure you're not a robot";

    private static final String ROBOT_CHECK_TITLE_MARKER =
        "<title>robot check</title>";

    private static final String AUTOMATED_ACCESS_BLOCK_MARKER =
        "automated access to amazon data";

    /**
     * Retorna a restrição encontrada ou null quando nenhuma evidência
     * explícita conhecida estiver presente.
     */
    public SourceRestrictionType detect(
        String content
    ) {

        Objects.requireNonNull(
            content,
            "content must not be null"
        );

        String normalized =
            normalizeContent(
                content
            );

        /*
         * CAPTCHA é avaliado primeiro porque algumas páginas também
         * contêm linguagem de verificação de robô.
         */
        if (normalized.contains(
            CAPTCHA_FORM_MARKER
        )
            || normalized.contains(
            CAPTCHA_INSTRUCTION_MARKER
        )
            || normalized.contains(
            CAPTCHA_IMAGE_INSTRUCTION_MARKER
        )) {

            return SourceRestrictionType.CAPTCHA;
        }

        if (normalized.contains(
            ROBOT_CHALLENGE_MARKER
        )
            || normalized.contains(
            ROBOT_CHECK_TITLE_MARKER
        )) {

            return SourceRestrictionType.CHALLENGE;
        }

        if (normalized.contains(
            AUTOMATED_ACCESS_BLOCK_MARKER
        )) {

            return SourceRestrictionType.BLOCKED;
        }

        return null;
    }

    private String normalizeContent(
        String content
    ) {

        return content
            .toLowerCase(
                Locale.ROOT
            )
            .replaceAll(
                "\\s+",
                " "
            )
            .trim();
    }
}
