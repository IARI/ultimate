/*
 * Copyright (C) 2013-2015 Jochen Hoenicke (hoenicke@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE PEAtoBoogie plug-in.
 *
 * The ULTIMATE PEAtoBoogie plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE PEAtoBoogie plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE PEAtoBoogie plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE PEAtoBoogie plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE PEAtoBoogie plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.pea2boogie.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.boogie.BoogieLocation;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression.Operator;
import de.uni_freiburg.informatik.ultimate.lib.pea.CDD;
import de.uni_freiburg.informatik.ultimate.lib.pea.Phase;
import de.uni_freiburg.informatik.ultimate.lib.pea.PhaseEventAutomata;
import de.uni_freiburg.informatik.ultimate.lib.pea.Transition;
import de.uni_freiburg.informatik.ultimate.pea2boogie.translator.CDDTranslator;
import de.uni_freiburg.informatik.ultimate.pea2boogie.translator.Translator;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IdentifierExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IntegerLiteral;
import de.uni_freiburg.informatik.ultimate.util.datastructures.Permutation;

public class ConditionGenerator {
	public Translator translator;

	public Expression nonDLCGenerator(final PhaseEventAutomata[] automata, final int[] automataPermutation,
			final String fileName, final BoogieLocation bl) {
		final int[][] phases = new int[automataPermutation.length][];
		for (int i = 0; i < automataPermutation.length; i++) {
			final PhaseEventAutomata automaton = automata[automataPermutation[i]];
			final int phaseCount = automaton.getPhases().length;
			phases[i] = new int[phaseCount];
			for (int j = 0; j < phaseCount; j++) {
				phases[i][j] = j;
			}
		}

		final List<int[]> phasePermutations = Permutation.crossProduct(phases);
		final List<Expression> conditions = new ArrayList<>();
		for (final int[] vector : phasePermutations) {
			assert (vector.length == automataPermutation.length);
			CDD cddOuter = CDD.TRUE;
			final List<Expression> impliesLHS = new ArrayList<>();
			for (int j = 0; j < vector.length; j++) {
				CDD cddInner = CDD.FALSE;
				final PhaseEventAutomata automaton = automata[automataPermutation[j]];
				final Phase phase = automaton.getPhases()[vector[j]];
				final List<Transition> transitions = phase.getTransitions();
				for (int k = 0; k < transitions.size(); k++) {
					cddInner = cddInner.or(genIntersectionAll(genGuardANDPrimedStInv(transitions.get(k)),
							genStrictInv(transitions.get(k))));
				}
				cddOuter = cddOuter.and(cddInner);
				impliesLHS.add(genPCCompEQ(automataPermutation[j], vector[j], fileName, bl));
			}
			final CDD cdd = new VarRemoval().excludeEventsAndPrimedVars(cddOuter, translator.primedVars);
			if (cdd == CDD.TRUE) {
				continue;
			}
			final Expression impliesRHS = new CDDTranslator().CDD_To_Boogie(cdd, fileName, bl);
			final Expression implies = new BinaryExpression(bl, BinaryExpression.Operator.LOGICIMPLIES,
					buildBinaryExpression(bl, BinaryExpression.Operator.LOGICAND, impliesLHS), impliesRHS);
			conditions.add(implies);
		}
		if (conditions.isEmpty()) {
			return null;
		}
		return buildBinaryExpression(bl, BinaryExpression.Operator.LOGICAND, conditions);
	}

	private static Expression buildBinaryExpression(final BoogieLocation bl, final Operator op,
			final List<Expression> conditions) {
		assert (!conditions.isEmpty());
		int offset = conditions.size() - 1;
		Expression result = conditions.get(offset);
		while (offset > 0) {
			offset--;
			result = new BinaryExpression(bl, op, conditions.get(offset), result);
		}
		return result;
	}

	/*
	 * public Expression nonDLCGeneratorToy (PhaseEventAutomata[] automata, String fileName, BoogieLocation bl) {
	 * 
	 * BoogieLocation blAssert = new BoogieLocation (fileName, 0, 0, 0, 0, bl);
	 * 
	 * Expression OrExprOuter = new BooleanLiteral(blAssert, false); Expression ANDExprInner = new
	 * BooleanLiteral(blAssert, true); Expression ANDExprOuter = new BooleanLiteral(blAssert, true); CDD OrCDDInner =
	 * CDD.FALSE; CDD AndCDDInner = CDD.TRUE;
	 * 
	 * for (int i = 0; i < automata.length; i++) { PhaseEventAutomata automaton = automata[i]; Phase[] phases =
	 * automaton.getPhases(); for (int j = 0; j < phases.length; j++) { List<Transition> transitions =
	 * phases[j].getTransitions(); for (int k = 0; k < transitions.size(); k++) { CDD cddInner = genPropsIntersection
	 * (genGuardANDPrimedStInv(transitions.get(k)), genStrictInv(transitions.get(k))); OrCDDInner =
	 * OrCDDInner.or(cddInner); } AndCDDInner = genPCCompEQ(i, j).and(OrCDDInner); OrCDDInner = CDD.FALSE; ANDExprInner
	 * = new CDDTranslator().CDD_To_Boogie(AndCDDInner, fileName, blAssert); if (j == 0) { OrExprOuter = ANDExprInner; }
	 * else { OrExprOuter = new BinaryExpression(bl, BinaryExpression.Operator.LOGICOR, ANDExprInner, OrExprOuter); } }
	 * if (i == 0) { ANDExprOuter = OrExprOuter; } else { ANDExprOuter = new BinaryExpression(bl,
	 * BinaryExpression.Operator.LOGICAND, OrExprOuter, ANDExprOuter); } } return ANDExprOuter; }
	 */
	public void setTranslator(final Translator translator) {
		this.translator = translator;
	}

	public static CDD genIntersectionAll(final CDD cdd1, final CDD cdd2) {
		return (cdd1.and(cdd2));
	}

	public static CDD genStrictInv(final Transition transition) {
		final Phase phase = transition.getDest();
		final String[] resetVars = transition.getResets();
		final List<String> resetList = Arrays.asList(resetVars);
		final CDD cdd = new StrictInvariant().genStrictInv(phase.getClockInvariant(), resetList);
		return cdd;
	}

	public static CDD genGuardANDPrimedStInv(final Transition transition) {
		final CDD guard = transition.getGuard();
		final Phase phase = transition.getDest();
		final CDD primedStInv = phase.getStateInvariant().prime();
		final CDD cdd = guard.and(primedStInv);
		// cdd = new VarRemoval().varRemoval(cdd, this.translator.primedVars, this.translator.eventVars);
		return cdd;
	}

	public static Expression genPCCompEQ(final int autIndex, final int phaseIndex, final String fileName,
			final BoogieLocation bl) {
		final IdentifierExpression identifier = new IdentifierExpression(bl, "pc" + autIndex);
		final IntegerLiteral intLiteral = new IntegerLiteral(bl, Integer.toString(phaseIndex));
		final BinaryExpression binaryExpr =
				new BinaryExpression(bl, BinaryExpression.Operator.COMPEQ, identifier, intLiteral);
		return binaryExpr;
	}
}
