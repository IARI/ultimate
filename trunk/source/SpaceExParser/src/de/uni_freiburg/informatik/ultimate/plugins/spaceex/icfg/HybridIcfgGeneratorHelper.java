package de.uni_freiburg.informatik.ultimate.plugins.spaceex.icfg;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.preferences.SpaceExPreferenceManager;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.util.FirstOrderLinearODE;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.util.SpaceExMathHelper;

public class HybridIcfgGeneratorHelper {
	
	private final ILogger mLogger;
	private final HybridVariableManager mVariableManager;
	private final SpaceExPreferenceManager mPreferenceManager;
	
	public HybridIcfgGeneratorHelper(final HybridVariableManager variableManager,
			final SpaceExPreferenceManager preferenceManager, final ILogger logger) {
		mLogger = logger;
		mVariableManager = variableManager;
		mPreferenceManager = preferenceManager;
	}
	
	/**
	 * function to check if a flow in infix noation is constant
	 * 
	 * @param flow
	 * @return
	 */
	public boolean isConstantFlow(final String flow) {
		final String[] split = flow.replaceAll("\\s", "").split("==");
		final List<String> rhs = SpaceExMathHelper.expressionToArray(split[1]);
		for (final String el : rhs) {
			// check if element is variable and no constant
			if (mVariableManager.getVar2ProgramVar().containsKey(el) && !mVariableManager.getConstants().contains(el)) {
				mLogger.debug("flow " + flow + "contains variables, not constant!");
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Function that analyses all parts of the flow and appends (currently) only the linear constant ODE solutions
	 * 
	 * @param flowInfix
	 * @return
	 */
	public String buildFlowInfix(final String flowInfix) {
		final StringBuilder sb = new StringBuilder();
		final String[] splittedFlow = !flowInfix.isEmpty() ? flowInfix.split("(&&)|(&)") : new String[0];
		if (splittedFlow.length > 0) {
			for (final String flow : splittedFlow) {
				if (isConstantFlow(flow)) {
					if (sb.length() > 0) {
						sb.append("&");
					}
					final FirstOrderLinearODE ode = new FirstOrderLinearODE(flow, HybridIcfgGenerator.TIME_VAR);
					sb.append(ode.getmSolution());
					sb.append(sb.length() == 0 ? "" : "&" + HybridIcfgGenerator.TIME_INV);
				}
			}
			mLogger.debug("FLOW TERMS: " + sb.toString());
		}
		return sb.toString();
	}
	
	/**
	 * function that replaces constants with their value before building the transformula. if you don't do this, SMT
	 * will throw an exeption if it tries to solve terms like x=0*t-constvar*t
	 * 
	 * @param infix
	 * @param currentGroupID
	 * @return
	 */
	public String replaceConstantValues(final String infix, final int currentGroupID) {
		String res = infix;
		if (mPreferenceManager.getGroupTodirectAssingment().containsKey(currentGroupID)) {
			final Map<String, String> assingmentMap =
					mPreferenceManager.getGroupTodirectAssingment().get(currentGroupID);
			for (final Entry<String, String> entry : assingmentMap.entrySet()) {
				if (mVariableManager.getConstants().contains(entry.getKey())) {
					res = res.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
				}
			}
		}
		return res;
	}
	
	/**
	 * Function that creates a IcfgInternalTransition.
	 * 
	 * @param start
	 * @param end
	 * @param transformula
	 * @return
	 */
	public IcfgInternalTransition createIcfgTransition(final IcfgLocation start, final IcfgLocation end,
			final UnmodifiableTransFormula transformula) {
		final IcfgInternalTransition trans = new IcfgInternalTransition(start, end, null, transformula);
		start.addOutgoing(trans);
		end.addIncoming(trans);
		return trans;
	}
}
