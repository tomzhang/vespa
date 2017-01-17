// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import com.yahoo.vespa.indexinglanguage.linguistics.LinguisticsAnnotator;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class TokenizeExpression extends Expression {

    private final Linguistics linguistics;
    private final AnnotatorConfig config;

    public TokenizeExpression(Linguistics linguistics, AnnotatorConfig config) {
        this.linguistics = linguistics;
        this.config = config;
    }

    public Linguistics getLinguistics() {
        return linguistics;
    }

    public AnnotatorConfig getConfig() {
        return config;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        StringFieldValue output = ((StringFieldValue)context.getValue()).clone();
        context.setValue(output);

        AnnotatorConfig cfg = new AnnotatorConfig(config);
        Language lang = context.resolveLanguage(linguistics);
        if (lang != null) {
            cfg.setLanguage(lang);
        }
        LinguisticsAnnotator annotator = new LinguisticsAnnotator(linguistics, cfg);
        annotator.annotate(output);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        // empty
    }

    @Override
    public DataType requiredInputType() {
        return DataType.STRING;
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("tokenize");
        if (config.getRemoveAccents()) {
            ret.append(" normalize");
        }
        if (config.getStemMode() != StemMode.NONE) {
            ret.append(" stem:\""+config.getStemMode()+"\"");
        }
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TokenizeExpression)) {
            return false;
        }
        TokenizeExpression rhs = (TokenizeExpression)obj;
        if (!config.equals(rhs.config)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + config.hashCode();
    }
}
