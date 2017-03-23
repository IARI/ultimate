/*
 * Copyright (C) 2013-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 * 
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.normalForms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.boogie.preprocessor.Activator;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.AnnotatedTerm;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermTransformer;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.Substitution;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;

/**
 * Transform Boolean Term into negation normal form.
 * 
 * @author heizmann@informatik.uni-freiburg.de
 */
public class Nnf {

	protected final Script mScript;
	private static final String s_FreshVariableString = "nnf";
	private final ManagedScript mMgdScript;
	protected final ILogger mLogger;
	private final NnfTransformerHelper mNnfTransformerHelper;
	private List<List<TermVariable>> mQuantifiedVariables;

	public enum QuantifierHandling {
		CRASH, PULL, KEEP, IS_ATOM
	}

	protected final QuantifierHandling mQuantifierHandling;

	public Nnf(final ManagedScript mgdScript, final IUltimateServiceProvider services,
			final QuantifierHandling quantifierHandling) {
		super();
		mQuantifierHandling = quantifierHandling;
		mScript = mgdScript.getScript();
		mMgdScript = mgdScript;
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mNnfTransformerHelper = getNnfTransformerHelper(services);
	}

	protected NnfTransformerHelper getNnfTransformerHelper(final IUltimateServiceProvider services) {
		return new NnfTransformerHelper(services);
	}

	public Term transform(final Term term) {
		assert mQuantifiedVariables == null;
		if (mQuantifierHandling == QuantifierHandling.PULL) {
			mQuantifiedVariables = new ArrayList<>();
			final List<TermVariable> firstQuantifierBlock = new ArrayList<>();
			mQuantifiedVariables.add(firstQuantifierBlock);
		}
		Term result = mNnfTransformerHelper.transform(term);
		if (mQuantifierHandling == QuantifierHandling.PULL) {
			for (int i = 0; i < mQuantifiedVariables.size(); i++) {
				final TermVariable[] variables =
						mQuantifiedVariables.get(i).toArray(new TermVariable[mQuantifiedVariables.get(i).size()]);
				if (variables.length > 0) {
					final int quantor = i % 2;
					result = mScript.quantifier(quantor, variables, result);
				}
			}
			mQuantifiedVariables = null;
		}
		assert Util.checkSat(mScript,
				mScript.term("distinct", term, result)) != LBool.SAT : "Nnf transformation unsound";
		return result;
	}

	protected class NnfTransformerHelper extends TermTransformer {

		protected IUltimateServiceProvider mServices;

		protected NnfTransformerHelper(final IUltimateServiceProvider services) {
			mServices = services;
		}

		@Override
		protected void convert(final Term term) {
			assert term.getSort().getName().equals("Bool") : "Input is not Bool";
			if (term instanceof ApplicationTerm) {
				final ApplicationTerm appTerm = (ApplicationTerm) term;
				final String functionName = appTerm.getFunction().getName();
				if (functionName.equals("and")) {
					final Term flattened = Util.and(mScript, appTerm.getParameters());
					if (SmtUtils.isFunctionApplication(flattened, "and")) {
						super.convert(flattened);
					} else {
						// term was simplified by flattening, top function
						// symbol changed, call convert again
						convert(flattened);
					}
					return;
				} else if (functionName.equals("or")) {
					final Term flattened = Util.or(mScript, appTerm.getParameters());
					if (SmtUtils.isFunctionApplication(flattened, "or")) {
						super.convert(flattened);
					} else {
						// term was simplified by flattening, top function
						// symbol changed, call convert again
						convert(flattened);
					}
					return;
				} else if (functionName.equals("not")) {
					assert appTerm.getParameters().length == 1;
					final Term notParam = appTerm.getParameters()[0];
					convertNot(notParam, term);
					return;
				} else if (functionName.equals("=>")) {
					final Term[] params = appTerm.getParameters();
					// we deliberately call convert() instead of super.convert()
					// the argument of this call might have been simplified
					// to a term whose function symbol is neither "and" nor "or"
					convert(Util.or(mScript, negateAllButLast(params)));
					return;
				} else if (functionName.equals("=") && SmtUtils.firstParamIsBool(appTerm)) {
					final Term[] params = appTerm.getParameters();
					if (params.length > 2) {
						final Term binarized = SmtUtils.binarize(mScript, appTerm);
						// we deliberately call convert() instead of super.convert()
						// the argument of this call might have been simplified
						// to a term whose function symbol is neither "and" nor "or"
						convert(binarized);
					} else {
						assert params.length == 2;
						// we deliberately call convert() instead of super.convert()
						// the argument of this call might have been simplified
						// to a term whose function symbol is neither "and" nor "or"
						convert(SmtUtils.binaryBooleanEquality(mScript, params[0], params[1]));
					}
				} else if (isXor(appTerm, functionName)) {
					final Term[] params = appTerm.getParameters();
					if (params.length > 2) {
						final Term binarized = SmtUtils.binarize(mScript, appTerm);
						// we deliberately call convert() instead of super.convert()
						// the argument of this call might have been simplified
						// to a term whose function symbol is neither "and" nor "or"
						convert(binarized);
					} else {
						assert params.length == 2;
						// we deliberately call convert() instead of super.convert()
						// the argument of this call might have been simplified
						// to a term whose function symbol is neither "and" nor "or"
						convert(SmtUtils.binaryBooleanNotEquals(mScript, params[0], params[1]));
					}
				} else if (functionName.equals("ite") && SmtUtils.allParamsAreBool(appTerm)) {
					final Term[] params = appTerm.getParameters();
					assert params.length == 3;
					final Term condTerm = params[0];
					final Term ifTerm = params[1];
					final Term elseTerm = params[2];
					final Term result = convertIte(condTerm, ifTerm, elseTerm);
					// we deliberately call convert() instead of super.convert()
					// the argument of this call might have been simplified
					// to a term whose function symbol is neither "and" nor "or"
					convert(result);
					return;
				} else {
					// consider term as atom
					setResult(term);
					return;
				}
			} else if (term instanceof TermVariable) {
				// consider term as atom
				setResult(term);
			} else if (term instanceof ConstantTerm) {
				// consider term as atom
				setResult(term);
			} else if (term instanceof QuantifiedFormula) {
				switch (mQuantifierHandling) {
				case CRASH: {
					throw new UnsupportedOperationException("quantifier handling set to " + mQuantifierHandling);
				}
				case IS_ATOM: {
					// consider quantified formula as atom
					setResult(term);
					return;
				}
				case KEEP: {
					super.convert(term);
					return;
				}
				case PULL: {
					final QuantifiedFormula qf = (QuantifiedFormula) term;
					final List<TermVariable> variables;
					if (mQuantifiedVariables.size() - 1 == qf.getQuantifier()) {
						variables = mQuantifiedVariables.get(mQuantifiedVariables.size() - 1);
					} else {
						variables = new ArrayList<>();
						mQuantifiedVariables.add(variables);
					}
					final Map<Term, Term> substitutionMapping = new HashMap<>();
					for (final TermVariable oldTv : qf.getVariables()) {
						final TermVariable freshTv =
								mMgdScript.constructFreshTermVariable(s_FreshVariableString, oldTv.getSort());
						substitutionMapping.put(oldTv, freshTv);
						variables.add(freshTv);
					}
					final Term newBody = new Substitution(mScript, substitutionMapping).transform(qf.getSubformula());
					// we deliberately call convert() instead of super.convert()
					// the argument of this call might have been simplified
					// to a term whose function symbol is neither "and" nor "or"
					convert(newBody);
					return;
				}
				default:
					throw new AssertionError("unknown case");
				}
			} else if (term instanceof AnnotatedTerm) {
				mLogger.warn("thrown away annotations " + Arrays.toString(((AnnotatedTerm) term).getAnnotations()));
				convert(((AnnotatedTerm) term).getSubterm());
			}
		}

		private Term convertIte(final Term condTerm, final Term ifTerm, final Term elseTerm) {
			final Term condImpliesIf = Util.or(mScript, SmtUtils.not(mScript, condTerm), ifTerm);
			final Term notCondImpliesElse = Util.or(mScript, condTerm, elseTerm);
			final Term result = Util.and(mScript, condImpliesIf, notCondImpliesElse);
			return result;
		}

		/**
		 * A function is an xor if one of the following applies.
		 * <ul>
		 * <li>its functionName is <b>xor</b>
		 * <li>its functionName is <b>distinct</b> and its parameters have Sort Bool.
		 * </ul>
		 */
		private boolean isXor(final ApplicationTerm appTerm, final String functionName) {
			return functionName.equals("xor") || functionName.equals("distinct") && SmtUtils.firstParamIsBool(appTerm);
		}

		private void convertNot(final Term notParam, final Term notTerm) {
			assert notParam.getSort().getName().equals("Bool") : "Input is not Bool";
			if (notParam instanceof ApplicationTerm) {
				final ApplicationTerm appTerm = (ApplicationTerm) notParam;
				final String functionName = appTerm.getFunction().getName();
				final Term[] params = appTerm.getParameters();
				if (functionName.equals("and")) {
					// we deliberately call convert() instead of super.convert()
					// the argument of this call might have been simplified
					// to a term whose function symbol is neither "and" nor "or"
					convert(Util.or(mScript, negateTerms(params)));
					return;
				} else if (functionName.equals("or")) {
					// we deliberately call convert() instead of super.convert()
					// the argument of this call might have been simplified
					// to a term whose function symbol is neither "and" nor "or"
					convert(Util.and(mScript, negateTerms(params)));
					return;
				} else if (functionName.equals("not")) {
					assert appTerm.getParameters().length == 1;
					final Term notnotParam = appTerm.getParameters()[0];
					// we deliberately call convert() instead of super.convert()
					// the argument of this call might have been simplified
					// to a term whose function symbol is neither "and" nor "or"
					convert(notnotParam);
					return;
				} else if (functionName.equals("=>")) {
					// we deliberately call convert() instead of super.convert()
					// the argument of this call might have been simplified
					// to a term whose function symbol is neither "and" nor "or"
					convert(Util.and(mScript, negateLast(params)));
					return;
				} else if (functionName.equals("=") && SmtUtils.firstParamIsBool(appTerm)) {
					final Term[] notParams = appTerm.getParameters();
					if (notParams.length > 2) {
						final Term binarized = SmtUtils.binarize(mScript, appTerm);
						// we deliberately call convert() instead of super.convert()
						// the argument of this call might have been simplified
						// to a term whose function symbol is neither "and" nor "or"
						convert(SmtUtils.not(mScript, binarized));
					} else {
						assert notParams.length == 2;
						// we deliberately call convert() instead of super.convert()
						// the argument of this call might have been simplified
						// to a term whose function symbol is neither "and" nor "or"
						convert(SmtUtils.binaryBooleanNotEquals(mScript, notParams[0], notParams[1]));
					}
				} else if (isXor(appTerm, functionName)) {
					final Term[] notParams = appTerm.getParameters();
					if (notParams.length > 2) {
						final Term binarized = SmtUtils.binarize(mScript, appTerm);
						// we deliberately call convert() instead of super.convert()
						// the argument of this call might have been simplified
						// to a term whose function symbol is neither "and" nor "or"
						convert(SmtUtils.not(mScript, binarized));
					} else {
						assert notParams.length == 2;
						// we deliberately call convert() instead of super.convert()
						// the argument of this call might have been simplified
						// to a term whose function symbol is neither "and" nor "or"
						convert(SmtUtils.binaryBooleanEquality(mScript, notParams[0], notParams[1]));
					}
				} else if (functionName.equals("ite") && SmtUtils.allParamsAreBool(appTerm)) {
					final Term[] notParams = appTerm.getParameters();
					assert params.length == 3;
					final Term condTerm = notParams[0];
					final Term ifTerm = notParams[1];
					final Term elseTerm = notParams[2];
					final Term convertedIte = convertIte(condTerm, ifTerm, elseTerm);
					convertNot(convertedIte, SmtUtils.not(mScript, convertedIte));
				} else {
					// consider original term as atom
					setResult(notTerm);
					return;
				}
			} else if (notParam instanceof ConstantTerm) {
				// consider term as atom
				setResult(notTerm);
			} else if (notParam instanceof TermVariable) {
				// consider term as atom
				setResult(notTerm);
			} else if (notParam instanceof QuantifiedFormula) {
				switch (mQuantifierHandling) {
				case CRASH: {
					throw new UnsupportedOperationException("quantifier handling set to " + mQuantifierHandling);
				}
				case IS_ATOM: {
					// consider quantified formula as atom
					setResult(notParam);
					return;
				}
				case KEEP: {
					final QuantifiedFormula qf = (QuantifiedFormula) notParam;
					final int quantor = (qf.getQuantifier() + 1) % 2;
					final TermVariable[] vars = qf.getVariables();
					final Term body = SmtUtils.not(mScript, qf.getSubformula());
					final Term negated = mScript.quantifier(quantor, vars, body);
					super.convert(negated);
					return;
				}
				case PULL: {
					throw new UnsupportedOperationException("cannot pull quantifier from negated formula");
				}
				default:
					throw new AssertionError("unknown quantifier handling");
				}
			} else {
				throw new UnsupportedOperationException("Unsupported " + notParam.getClass());
			}
		}

		private Term[] negateTerms(final Term[] terms) {
			final Term[] newTerms = new Term[terms.length];
			for (int i = 0; i < terms.length; i++) {
				newTerms[i] = SmtUtils.not(mScript, terms[i]);
			}
			return newTerms;
		}

		private Term[] negateLast(final Term[] terms) {
			final Term[] newTerms = new Term[terms.length];
			System.arraycopy(terms, 0, newTerms, 0, terms.length - 1);
			newTerms[terms.length - 1] = SmtUtils.not(mScript, terms[terms.length - 1]);
			return newTerms;
		}

		private Term[] negateAllButLast(final Term[] terms) {
			final Term[] newTerms = new Term[terms.length];
			for (int i = 0; i < terms.length - 1; i++) {
				newTerms[i] = SmtUtils.not(mScript, terms[i]);
			}
			newTerms[terms.length - 1] = terms[terms.length - 1];
			return newTerms;
		}

		@Override
		public void convertApplicationTerm(final ApplicationTerm appTerm, final Term[] newArgs) {
			final Term simplified = SmtUtils.termWithLocalSimplification(mScript, appTerm.getFunction().getName(),
					appTerm.getFunction().getIndices(), newArgs);
			setResult(simplified);
		}

	}

}
