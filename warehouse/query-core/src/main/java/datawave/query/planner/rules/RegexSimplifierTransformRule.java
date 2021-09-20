package datawave.query.planner.rules;

import datawave.query.config.ShardQueryConfiguration;
import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.functions.EvaluationPhaseFilterFunctions;
import datawave.query.jexl.functions.EvaluationPhaseFilterFunctionsDescriptor;
import datawave.query.jexl.functions.FunctionJexlNodeVisitor;
import datawave.query.util.MetadataHelper;
import org.apache.commons.jexl2.parser.ASTERNode;
import org.apache.commons.jexl2.parser.ASTFunctionNode;
import org.apache.commons.jexl2.parser.ASTNRNode;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.log4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is intended to simplify when multiple .* or .*? are consecutively placed in a regex. The reason we need to simplify this is because there is an
 * exponential increase in the time to match relative to the number of consecutive .* or .*? and to the size of the value. This has been witnessed to take over
 * many days to complete with 8 consecutive .*? and a value length on the order of 1K+.
 */
public class RegexSimplifierTransformRule implements NodeTransformRule {
    private static final Logger log = Logger.getLogger(RegexSimplifierTransformRule.class);
    private Pattern pattern = Pattern.compile("(\\.\\*\\??){2,}");
    
    @Override
    public JexlNode apply(JexlNode node, ShardQueryConfiguration config, MetadataHelper helper) {
        if (node instanceof ASTERNode || node instanceof ASTNRNode) {
            JexlNode literal = JexlASTHelper.getLiteral(node);
            literal.image = processPattern(literal.image);
        } else if (node instanceof ASTFunctionNode) {
            FunctionJexlNodeVisitor functionMetadata = new FunctionJexlNodeVisitor();
            node.jjtAccept(functionMetadata, null);
            if (functionMetadata.namespace().equals(EvaluationPhaseFilterFunctions.EVAL_PHASE_FUNCTION_NAMESPACE)
                            && EvaluationPhaseFilterFunctionsDescriptor.EvaluationPhaseFilterJexlArgumentDescriptor.regexFunctions.contains(functionMetadata
                                            .name())) {
                JexlNode literal = JexlASTHelper.getLiteral(functionMetadata.args().get(1));
                literal.image = processPattern(literal.image);
            }
        }
        return node;
    }
    
    private String processPattern(String regex) {
        boolean changed = false;
        Matcher matcher = pattern.matcher(regex);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, ".*?");
            changed = true;
        }
        if (changed) {
            matcher.appendTail(sb);
            return sb.toString();
        }
        return regex;
    }
}
