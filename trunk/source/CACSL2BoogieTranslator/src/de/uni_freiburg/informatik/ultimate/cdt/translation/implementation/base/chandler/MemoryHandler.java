/*
 * Copyright (C) 2013-2015 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 Markus Lindenmann (lindenmm@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE CACSL2BoogieTranslator plug-in.
 * 
 * The ULTIMATE CACSL2BoogieTranslator plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE CACSL2BoogieTranslator plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE CACSL2BoogieTranslator plug-in. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE CACSL2BoogieTranslator plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE CACSL2BoogieTranslator plug-in grant you additional permission
 * to convey the resulting work.
 */
/**
 * Class that handles translation of memory related operations.
 */
package de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.chandler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;

import de.uni_freiburg.informatik.ultimate.boogie.ExpressionFactory;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ASTType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayAccessExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayLHS;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayStoreExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssignmentStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssumeStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Attribute;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression.Operator;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BitvecLiteral;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Body;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BooleanLiteral;
import de.uni_freiburg.informatik.ultimate.boogie.ast.CallStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Declaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.EnsuresSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.HavocStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IdentifierExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IntegerLiteral;
import de.uni_freiburg.informatik.ultimate.boogie.ast.LeftHandSide;
import de.uni_freiburg.informatik.ultimate.boogie.ast.LoopInvariantSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ModifiesSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.PrimitiveType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Procedure;
import de.uni_freiburg.informatik.ultimate.boogie.ast.RequiresSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Specification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.StructAccessExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.StructConstructor;
import de.uni_freiburg.informatik.ultimate.boogie.ast.UnaryExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VarList;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableDeclaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.boogie.ast.WhileStatement;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.CACSLLocation;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.LocationFactory;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.CHandler;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.TypeHandler;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.chandler.AMemoryModel.ReadWriteDefinition;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.expressiontranslation.AExpressionTranslation;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CArray;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CEnum;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CNamed;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPointer;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPrimitive;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPrimitive.CPrimitives;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CStruct;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CType;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.exception.UnsupportedSyntaxException;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.ExpressionResult;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.HeapLValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.LRValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.LocalLValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.RValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.util.SFO;
import de.uni_freiburg.informatik.ultimate.cdt.translation.interfaces.Dispatcher;
import de.uni_freiburg.informatik.ultimate.cdt.translation.interfaces.handler.INameHandler;
import de.uni_freiburg.informatik.ultimate.cdt.translation.interfaces.handler.ITypeHandler;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check.Spec;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Overapprox;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.plugins.generator.cacsl2boogietranslator.preferences.CACSLPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.cacsl2boogietranslator.preferences.CACSLPreferenceInitializer.MemoryModel;
import de.uni_freiburg.informatik.ultimate.plugins.generator.cacsl2boogietranslator.preferences.CACSLPreferenceInitializer.PointerCheckMode;
import de.uni_freiburg.informatik.ultimate.util.datastructures.LinkedScopedHashMap;

/**
 * @author Markus Lindenmann
 */
public class MemoryHandler {

	private static final boolean SUPPORT_FLOATS_ON_HEAP = false;
	private static final String FLOAT_ON_HEAP_UNSOUND_MESSAGE =
			"Analysis for floating types on heap by default disabled (soundness first).";

	/**
	 * The "~size" variable identifier.
	 */
	private static final String SIZE = "~size";
	/**
	 * The "~addr" variable identifier.
	 */
	private static final String ADDR = "~addr";

	/**
	 * Add also implementations of malloc, free, write and read functions. TODO: details
	 */
	private static final boolean ADD_IMPLEMENTATIONS = false;

	private final PointerCheckMode mPointerBaseValidity;
	private final PointerCheckMode mCheckPointerSubtractionAndComparisonValidity;
	private final PointerCheckMode mPointerTargetFullyAllocated;
	private final boolean mCheckFreeValid;

	// needed for adding modifies clauses
	private final FunctionHandler mFunctionHandler;
	private final ITypeHandler mTypeHandler;

	/**
	 * This set contains those pointers that we have to malloc at the beginning of the current scope;
	 */
	private final LinkedScopedHashMap<LocalLValueILocationPair, Integer> mVariablesToBeMalloced;
	/**
	 * This set contains those pointers that we have to free at the end of the current scope;
	 */
	private final LinkedScopedHashMap<LocalLValueILocationPair, Integer> mVariablesToBeFreed;

	private final AExpressionTranslation mExpressionTranslation;

	private final TypeSizeAndOffsetComputer mTypeSizeAndOffsetComputer;
	private final TypeSizes mTypeSizes;
	private final RequiredMemoryModelFeatures mRequiredMemoryModelFeatures;
	private final AMemoryModel mMemoryModel;
	private final INameHandler mNameHandler;
	private final MemoryModel mMemoryModelPreference;
	private final IBooleanArrayHelper mBooleanArrayHelper;

	/**
	 * Constructor.
	 * 
	 * @param typeHandler
	 * @param checkPointerValidity
	 * @param typeSizeComputer
	 * @param bitvectorTranslation
	 * @param nameHandler
	 */
	public MemoryHandler(final ITypeHandler typeHandler, final FunctionHandler functionHandler,
			final boolean checkPointerValidity, final TypeSizeAndOffsetComputer typeSizeComputer,
			final TypeSizes typeSizes, final AExpressionTranslation expressionTranslation,
			final boolean bitvectorTranslation, final INameHandler nameHandler, final boolean smtBoolArrayWorkaround,
			final IPreferenceProvider prefs) {
		mTypeHandler = typeHandler;
		mTypeSizes = typeSizes;
		mFunctionHandler = functionHandler;
		mExpressionTranslation = expressionTranslation;
		mNameHandler = nameHandler;
		mRequiredMemoryModelFeatures = new RequiredMemoryModelFeatures();
		if (smtBoolArrayWorkaround) {
			if (bitvectorTranslation) {
				mBooleanArrayHelper = new BooleanArrayHelper_Bitvector();
			} else {
				mBooleanArrayHelper = new BooleanArrayHelper_Integer();
			}
		} else {
			mBooleanArrayHelper = new BooleanArrayHelper_Bool();
		}

		// read preferences from settings
		mPointerBaseValidity =
				prefs.getEnum(CACSLPreferenceInitializer.LABEL_CHECK_POINTER_VALIDITY, PointerCheckMode.class);
		mPointerTargetFullyAllocated =
				prefs.getEnum(CACSLPreferenceInitializer.LABEL_CHECK_POINTER_ALLOC, PointerCheckMode.class);
		mCheckFreeValid = prefs.getBoolean(CACSLPreferenceInitializer.LABEL_CHECK_FREE_VALID);
		mCheckPointerSubtractionAndComparisonValidity =
				prefs.getEnum(CACSLPreferenceInitializer.LABEL_CHECK_POINTER_SUBTRACTION_AND_COMPARISON_VALIDITY,
						PointerCheckMode.class);
		mMemoryModelPreference = prefs.getEnum(CACSLPreferenceInitializer.LABEL_MEMORY_MODEL, MemoryModel.class);
		final MemoryModel memoryModelPreference = mMemoryModelPreference;
		final AMemoryModel memoryModel = getMemoryModel(bitvectorTranslation, memoryModelPreference);
		mMemoryModel = memoryModel;
		mVariablesToBeMalloced = new LinkedScopedHashMap<>();
		mVariablesToBeFreed = new LinkedScopedHashMap<>();

		mTypeSizeAndOffsetComputer = typeSizeComputer;
	}

	private AMemoryModel getMemoryModel(final boolean bitvectorTranslation, final MemoryModel memoryModelPreference)
			throws AssertionError {
		final AMemoryModel memoryModel;
		if (bitvectorTranslation) {
			switch (memoryModelPreference) {
			case HoenickeLindenmann_1ByteResolution:
				memoryModel = new MemoryModel_SingleBitprecise(1, mTypeSizes, (TypeHandler) mTypeHandler,
						mExpressionTranslation);
				break;
			case HoenickeLindenmann_2ByteResolution:
				memoryModel = new MemoryModel_SingleBitprecise(2, mTypeSizes, (TypeHandler) mTypeHandler,
						mExpressionTranslation);
				break;
			case HoenickeLindenmann_4ByteResolution:
				memoryModel = new MemoryModel_SingleBitprecise(4, mTypeSizes, (TypeHandler) mTypeHandler,
						mExpressionTranslation);
				break;
			case HoenickeLindenmann_8ByteResolution:
				memoryModel = new MemoryModel_SingleBitprecise(8, mTypeSizes, (TypeHandler) mTypeHandler,
						mExpressionTranslation);
				break;
			case HoenickeLindenmann_Original:
				memoryModel = new MemoryModel_MultiBitprecise(mTypeSizes, mTypeHandler, mExpressionTranslation);
				break;
			default:
				throw new AssertionError("unknown value");
			}
		} else {
			switch (memoryModelPreference) {
			case HoenickeLindenmann_Original:
				memoryModel = new MemoryModel_Unbounded(mTypeSizes, mTypeHandler, mExpressionTranslation);
				break;
			case HoenickeLindenmann_1ByteResolution:
			case HoenickeLindenmann_2ByteResolution:
			case HoenickeLindenmann_4ByteResolution:
			case HoenickeLindenmann_8ByteResolution:
				throw new UnsupportedOperationException(
						"Memory model " + mMemoryModelPreference + " only available in bitprecise translation");
			default:
				throw new AssertionError("unknown value");
			}
		}
		return memoryModel;
	}

	public RequiredMemoryModelFeatures getRequiredMemoryModelFeatures() {
		return mRequiredMemoryModelFeatures;
	}

	public AMemoryModel getMemoryModel() {
		return mMemoryModel;
	}

	public Expression calculateSizeOf(final ILocation loc, final CType cType) {
		return mTypeSizeAndOffsetComputer.constructBytesizeExpression(loc, cType);
	}

	/**
	 * Declare all variables required for the memory model.
	 * 
	 * @param main
	 *            a reference to the main dispatcher.
	 * @param tuLoc
	 *            location to use for declarations.
	 * @return a set of declarations.
	 */
	public ArrayList<Declaration> declareMemoryModelInfrastructure(final Dispatcher main, final ILocation tuLoc) {
		final ArrayList<Declaration> decl = new ArrayList<>();
		if (!mRequiredMemoryModelFeatures.isMemoryModelInfrastructureRequired()
				&& mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations().isEmpty()) {
			return decl;
		}

		decl.add(constructNullPointerConstant());
		// TODO should we introduce the commented out conditions -- right now it seems safe to always declare the base
		// arrays and functions
		// if
		// (getRequiredMemoryModelFeatures().getRequiredMemoryModelDeclarations().contains(MemoryModelDeclarations.Ultimate_Valid))
		// {
		decl.add(constructValidArrayDeclaration());
		// }
		// if
		// (getRequiredMemoryModelFeatures().getRequiredMemoryModelDeclarations().contains(MemoryModelDeclarations.Ultimate_Length))
		// {
		decl.add(constuctLengthArrayDeclaration());
		// }

		final Collection<HeapDataArray> heapDataArrays = mMemoryModel.getDataHeapArrays(mRequiredMemoryModelFeatures);

		{// add memory arrays and read/write procedures
			for (final HeapDataArray heapDataArray : heapDataArrays) {
				decl.add(constructMemoryArrayDeclaration(tuLoc, heapDataArray.getName(), heapDataArray.getASTType()));
				// create and add read and write procedure
				decl.addAll(constructWriteProcedures(tuLoc, heapDataArrays, heapDataArray));
				decl.addAll(constructReadProcedures(tuLoc, heapDataArray));
			}
		}

		decl.addAll(declareFree(main, tuLoc));
		decl.addAll(declareDeallocation(main, tuLoc));

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.Ultimate_Alloc)) {
			decl.addAll(declareMalloc(mTypeHandler, tuLoc));
			mFunctionHandler.addCallGraphNode(MemoryModelDeclarations.Ultimate_Alloc.getName());
			mFunctionHandler.addModifiedGlobalEntry(MemoryModelDeclarations.Ultimate_Alloc.getName());
		}

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.C_Memset)) {
			decl.addAll(declareMemset(main, heapDataArrays));
			mFunctionHandler.addCallGraphNode(MemoryModelDeclarations.C_Memset.getName());
			mFunctionHandler.addModifiedGlobalEntry(MemoryModelDeclarations.C_Memset.getName());
		}

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.Ultimate_MemInit)) {
			decl.addAll(declareUltimateMeminit(main, heapDataArrays));
			mFunctionHandler.addCallGraphNode(MemoryModelDeclarations.Ultimate_MemInit.getName());
			mFunctionHandler.addModifiedGlobalEntry(MemoryModelDeclarations.Ultimate_MemInit.getName());
		}

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.C_Memcpy)) {
			decl.addAll(declareMemcpy(main, heapDataArrays));
			mFunctionHandler.addCallGraphNode(MemoryModelDeclarations.C_Memcpy.getName());
			mFunctionHandler.addModifiedGlobalEntry(MemoryModelDeclarations.C_Memcpy.getName());
		}
		return decl;
	}

	private VariableDeclaration constuctLengthArrayDeclaration() {
		// var #length : [int]int;
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ASTType pointerComponentType =
				mTypeHandler.cType2AstType(ignoreLoc, mExpressionTranslation.getCTypeOfPointerComponents());
		final ASTType lengthType =
				new ArrayType(ignoreLoc, new String[0], new ASTType[] { pointerComponentType }, pointerComponentType);
		final VarList vlL = new VarList(ignoreLoc, new String[] { SFO.LENGTH }, lengthType);
		return new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { vlL });
	}

	private VariableDeclaration constructValidArrayDeclaration() {
		// var #valid : [int]bool;
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ASTType pointerComponentType =
				mTypeHandler.cType2AstType(ignoreLoc, mExpressionTranslation.getCTypeOfPointerComponents());
		final ASTType validType = new ArrayType(ignoreLoc, new String[0], new ASTType[] { pointerComponentType },
				mBooleanArrayHelper.constructBoolReplacementType());
		final VarList vlV = new VarList(ignoreLoc, new String[] { SFO.VALID }, validType);
		return new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { vlV });
	}

	private VariableDeclaration constructNullPointerConstant() {
		// NULL Pointer
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final VariableDeclaration result = new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] {
				new VarList(ignoreLoc, new String[] { SFO.NULL }, mTypeHandler.constructPointerType(ignoreLoc)) });
		return result;
	}

	private List<Declaration> declareUltimateMeminit(final Dispatcher main,
			final Collection<HeapDataArray> heapDataArrays) {
		final ArrayList<Declaration> decls = new ArrayList<>();
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();

		final String inParamPtr = "#ptr";
		final String inParamAmountOfFields = "#amountOfFields";
		final String inParamSizeOfFields = "#sizeOfFields";
		final String inParamProduct = "#product";
		final String proc = MemoryModelDeclarations.Ultimate_MemInit.getName();

		final VarList inParamPtrVl =
				new VarList(ignoreLoc, new String[] { inParamPtr }, mTypeHandler.constructPointerType(ignoreLoc));
		final VarList inParamAmountOfFieldsVl = new VarList(ignoreLoc, new String[] { inParamAmountOfFields },
				mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSize_T()));
		final VarList inParamSizeOfFieldsVl = new VarList(ignoreLoc, new String[] { inParamSizeOfFields },
				mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSize_T()));
		final VarList inParamProductVl = new VarList(ignoreLoc, new String[] { inParamProduct },
				mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSize_T()));

		final VarList[] inParams =
				new VarList[] { inParamPtrVl, inParamAmountOfFieldsVl, inParamSizeOfFieldsVl, inParamProductVl };
		final VarList[] outParams = new VarList[] {};

		final List<VariableDeclaration> decl = new ArrayList<>();
		final CPrimitive sizeT = mTypeSizeAndOffsetComputer.getSize_T();
		final String loopCtr = mNameHandler.getTempVarUID(SFO.AUXVAR.LOOPCTR, sizeT);
		final ASTType astType = mTypeHandler.cType2AstType(ignoreLoc, sizeT);
		final VarList lcvl = new VarList(ignoreLoc, new String[] { loopCtr }, astType);
		final VariableDeclaration loopCtrDec =
				new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { lcvl });
		decl.add(loopCtrDec);

		final Expression zero = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
				new CPrimitive(CPrimitives.UCHAR), BigInteger.ZERO);
		final List<Statement> loopBody = constructMemsetLoopBody(heapDataArrays, loopCtr, inParamPtr, zero);

		final IdentifierExpression inParamProductExpr = new IdentifierExpression(ignoreLoc, inParamProduct);
		final Expression stepsize;
		if (mMemoryModel instanceof MemoryModel_SingleBitprecise) {
			final int resolution = ((MemoryModel_SingleBitprecise) mMemoryModel).getResolution();
			stepsize = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc, sizeT,
					BigInteger.valueOf(resolution));
		} else {
			final IdentifierExpression inParamSizeOfFieldsExpr =
					new IdentifierExpression(ignoreLoc, inParamSizeOfFields);
			stepsize = inParamSizeOfFieldsExpr;
		}

		final List<Statement> stmt = constructCountingLoop(inParamProductExpr, loopCtr, stepsize, loopBody);

		final Body procBody = new Body(ignoreLoc, decl.toArray(new VariableDeclaration[decl.size()]),
				stmt.toArray(new Statement[stmt.size()]));

		// make the specifications
		final ArrayList<Specification> specs = new ArrayList<>();

		// add modifies spec
		final ModifiesSpecification modifiesSpec = announceModifiedGlobals(proc, heapDataArrays);
		specs.add(modifiesSpec);

		// add the procedure declaration
		final Procedure memCpyProcDecl = new Procedure(ignoreLoc, new Attribute[0], proc, new String[0], inParams,
				outParams, specs.toArray(new Specification[specs.size()]), null);
		decls.add(memCpyProcDecl);

		// add the procedure implementation
		final Procedure memCpyProc =
				new Procedure(ignoreLoc, new Attribute[0], proc, new String[0], inParams, outParams, null, procBody);
		decls.add(memCpyProc);

		return decls;
	}

	public CallStatement constructUltimateMeminitCall(final ILocation loc, final Expression amountOfFields,
			final Expression sizeOfFields, final Expression product, final Expression pointer) {
		mRequiredMemoryModelFeatures.require(MemoryModelDeclarations.Ultimate_MemInit);
		return new CallStatement(loc, false, new VariableLHS[0], MemoryModelDeclarations.Ultimate_MemInit.getName(),
				new Expression[] { pointer, amountOfFields, sizeOfFields, product });
	}

	/**
	 * Tell mFunctionHandler that procedure proc modifies all heapDataArrays. Retruns modifies specification.
	 */
	private ModifiesSpecification announceModifiedGlobals(final String proc,
			final Collection<HeapDataArray> heapDataArrays) {
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ArrayList<VariableLHS> modifiesLHSs = new ArrayList<>();
		for (final HeapDataArray hda : heapDataArrays) {
			final String memArrayName = hda.getVariableName();
			modifiesLHSs.add(new VariableLHS(ignoreLoc, memArrayName));

			mFunctionHandler.addCallGraphNode(proc);
			mFunctionHandler.addModifiedGlobal(proc, memArrayName);
		}
		return new ModifiesSpecification(ignoreLoc, false, modifiesLHSs.toArray(new VariableLHS[modifiesLHSs.size()]));
	}

	/**
	 * Construct specification and implementation for our Boogie representation of the memcpy function defined in
	 * 7.24.2.1 of C11. void *memcpy(void * restrict s1, const void * restrict s2, size_t n);
	 * 
	 * @param main
	 * @param heapDataArrays
	 * @return
	 */
	private List<Declaration> declareMemcpy(final Dispatcher main, final Collection<HeapDataArray> heapDataArrays) {
		final ArrayList<Declaration> memCpyDecl = new ArrayList<>();
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();

		final String memcpyInParamSize = SFO.MEMCPY_SIZE;
		final String memcpyInParamDest = SFO.MEMCPY_DEST;
		final String memcpyInParamSrc = SFO.MEMCPY_SRC;
		final String memcpyOutParam = SFO.RES;

		final VarList inPDest = new VarList(ignoreLoc, new String[] { memcpyInParamDest },
				mTypeHandler.constructPointerType(ignoreLoc));
		final VarList inPSrc =
				new VarList(ignoreLoc, new String[] { memcpyInParamSrc }, mTypeHandler.constructPointerType(ignoreLoc));
		final VarList inPSize = new VarList(ignoreLoc, new String[] { memcpyInParamSize },
				mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSize_T()));
		final VarList outP =
				new VarList(ignoreLoc, new String[] { memcpyOutParam }, mTypeHandler.constructPointerType(ignoreLoc));
		final VarList[] inParams = new VarList[] { inPDest, inPSrc, inPSize };
		final VarList[] outParams = new VarList[] { outP };

		final List<VariableDeclaration> decl = new ArrayList<>();
		final CPrimitive sizeT = mTypeSizeAndOffsetComputer.getSize_T();
		final String loopCtr = mNameHandler.getTempVarUID(SFO.AUXVAR.LOOPCTR, sizeT);
		final ASTType astType = mTypeHandler.cType2AstType(ignoreLoc, sizeT);
		final VarList lcvl = new VarList(ignoreLoc, new String[] { loopCtr }, astType);
		final VariableDeclaration loopCtrDec =
				new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { lcvl });
		decl.add(loopCtrDec);

		final List<Statement> loopBody =
				constructMemcpyLoopBody(heapDataArrays, loopCtr, memcpyInParamDest, memcpyInParamSrc);

		final IdentifierExpression memcpyInParamSizeExpr = new IdentifierExpression(ignoreLoc, memcpyInParamSize);
		final Expression one = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ONE);
		final List<Statement> stmt = constructCountingLoop(memcpyInParamSizeExpr, loopCtr, one, loopBody);

		final Body procBody = new Body(ignoreLoc, decl.toArray(new VariableDeclaration[decl.size()]),
				stmt.toArray(new Statement[stmt.size()]));

		// make the specifications
		final ArrayList<Specification> specs = new ArrayList<>();

		// add modifies spec
		final ModifiesSpecification modifiesSpec =
				announceModifiedGlobals(MemoryModelDeclarations.C_Memcpy.getName(), heapDataArrays);
		specs.add(modifiesSpec);

		// add requires #valid[dest!base];
		addPointerBaseValidityCheck(ignoreLoc, memcpyInParamDest, specs);
		// add requires #valid[src!base];
		addPointerBaseValidityCheck(ignoreLoc, memcpyInParamSrc, specs);

		final Expression memcpyParamSizeExpr = new IdentifierExpression(ignoreLoc, memcpyInParamSize);

		// add requires (#size + #dest!offset <= #length[#dest!base] && 0 <= #dest!offset)
		checkPointerTargetFullyAllocated(ignoreLoc, memcpyParamSizeExpr, memcpyInParamDest, specs);

		// add requires (#size + #src!offset <= #length[#src!base] && 0 <= #src!offset)
		checkPointerTargetFullyAllocated(ignoreLoc, memcpyParamSizeExpr, memcpyInParamSrc, specs);

		// free ensures #res == dest;
		final EnsuresSpecification returnValue = new EnsuresSpecification(ignoreLoc, true,
				ExpressionFactory.newBinaryExpression(ignoreLoc, Operator.COMPEQ,
						new IdentifierExpression(ignoreLoc, memcpyOutParam),
						new IdentifierExpression(ignoreLoc, memcpyInParamDest)));
		specs.add(returnValue);

		// add the procedure declaration
		final Procedure memCpyProcDecl =
				new Procedure(ignoreLoc, new Attribute[0], MemoryModelDeclarations.C_Memcpy.getName(), new String[0],
						inParams, outParams, specs.toArray(new Specification[specs.size()]), null);
		memCpyDecl.add(memCpyProcDecl);

		// add the procedure implementation
		final Procedure memCpyProc = new Procedure(ignoreLoc, new Attribute[0],
				MemoryModelDeclarations.C_Memcpy.getName(), new String[0], inParams, outParams, null, procBody);
		memCpyDecl.add(memCpyProc);

		return memCpyDecl;
	}

	/**
	 * Returns call to our memcpy procedure and announces that memcpy is required by our memory model.
	 */
	public CallStatement constructMemcpyCall(final ILocation loc, final Expression dest, final Expression src,
			final Expression size, final String resVarId) {
		mRequiredMemoryModelFeatures.require(MemoryModelDeclarations.C_Memcpy);
		return new CallStatement(loc, false, new VariableLHS[] { new VariableLHS(loc, resVarId) },
				MemoryModelDeclarations.C_Memcpy.getName(), new Expression[] { dest, src, size });
	}

	/**
	 * Construct loop of the following form, where loopBody is a List of statements and the variables loopConterVariable
	 * and loopBoundVariable have the translated type of size_t.
	 * 
	 * loopConterVariable := 0; while (#t~loopctr4 < loopBoundVariable) { ___loopBody___ loopConterVariable :=
	 * loopConterVariable + 1; }
	 * 
	 * @param loopBoundVariableExpr
	 * @param loopCounterVariableId
	 * @param loopBody
	 * @return
	 */
	private ArrayList<Statement> constructCountingLoop(final Expression loopBoundVariableExpr,
			final String loopCounterVariableId, final Expression loopCounterIncrementExpr,
			final List<Statement> loopBody) {
		final CACSLLocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ArrayList<Statement> stmt = new ArrayList<>();

		// initialize the counter to 0
		final Expression zero = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
				mTypeSizeAndOffsetComputer.getSize_T(), BigInteger.ZERO);
		stmt.add(new AssignmentStatement(ignoreLoc,
				new LeftHandSide[] { new VariableLHS(ignoreLoc, loopCounterVariableId) }, new Expression[] { zero }));

		final IdentifierExpression loopCounterVariableExpr = new IdentifierExpression(ignoreLoc, loopCounterVariableId);

		final Expression condition = mExpressionTranslation.constructBinaryComparisonExpression(ignoreLoc,
				IASTBinaryExpression.op_lessThan, loopCounterVariableExpr, mTypeSizeAndOffsetComputer.getSize_T(),
				loopBoundVariableExpr, mTypeSizeAndOffsetComputer.getSize_T());

		final ArrayList<Statement> bodyStmt = new ArrayList<>();
		bodyStmt.addAll(loopBody);

		// increment counter
		final VariableLHS ctrLHS = new VariableLHS(ignoreLoc, loopCounterVariableId);
		final Expression counterPlusOne =
				mExpressionTranslation.constructArithmeticExpression(ignoreLoc, IASTBinaryExpression.op_plus,
						loopCounterVariableExpr, mExpressionTranslation.getCTypeOfPointerComponents(),
						loopCounterIncrementExpr, mExpressionTranslation.getCTypeOfPointerComponents());
		bodyStmt.add(
				new AssignmentStatement(ignoreLoc, new LeftHandSide[] { ctrLHS }, new Expression[] { counterPlusOne }));

		final Statement[] whileBody = bodyStmt.toArray(new Statement[bodyStmt.size()]);

		final WhileStatement whileStm =
				new WhileStatement(ignoreLoc, condition, new LoopInvariantSpecification[0], whileBody);
		stmt.add(whileStm);
		return stmt;
	}

	/**
	 * Return the assignments that we do in the loop body of our memcpy implementation.
	 * 
	 * #memory_int[{ base: dest!base, offset: dest!offset + #t~loopctr6 * 1 }] := #memory_int[{ base: src!base, offset:
	 * src!offset + #t~loopctr6 * 1 }];
	 * 
	 * @param heapDataArrays
	 * @param loopCtr
	 * @param destPtr
	 * @param srcPtr
	 * @return
	 */
	private ArrayList<Statement> constructMemcpyLoopBody(final Collection<HeapDataArray> heapDataArrays,
			final String loopCtr, final String destPtr, final String srcPtr) {

		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ArrayList<Statement> result = new ArrayList<>();

		final IdentifierExpression loopCtrExpr = new IdentifierExpression(ignoreLoc, loopCtr);
		final IdentifierExpression destPtrExpr = new IdentifierExpression(ignoreLoc, destPtr);
		final IdentifierExpression srcPtrExpr = new IdentifierExpression(ignoreLoc, srcPtr);

		final Expression currentDest = doPointerArithmetic(IASTBinaryExpression.op_plus, ignoreLoc, destPtrExpr,
				new RValue(loopCtrExpr, mExpressionTranslation.getCTypeOfPointerComponents()),
				new CPrimitive(CPrimitives.VOID));
		final Expression currentSrc = doPointerArithmetic(IASTBinaryExpression.op_plus, ignoreLoc, srcPtrExpr,
				new RValue(loopCtrExpr, mExpressionTranslation.getCTypeOfPointerComponents()),
				new CPrimitive(CPrimitives.VOID));
		for (final HeapDataArray hda : heapDataArrays) {
			final String memArrayName = hda.getVariableName();
			final ArrayAccessExpression srcAcc = new ArrayAccessExpression(ignoreLoc,
					new IdentifierExpression(ignoreLoc, memArrayName), new Expression[] { currentSrc });
			final ArrayLHS destAcc =
					new ArrayLHS(ignoreLoc, new VariableLHS(ignoreLoc, memArrayName), new Expression[] { currentDest });
			result.add(new AssignmentStatement(ignoreLoc, new LeftHandSide[] { destAcc }, new Expression[] { srcAcc }));

		}
		return result;
	}

	private ArrayList<Statement> constructMemsetLoopBody(final Collection<HeapDataArray> heapDataArrays,
			final String loopCtr, final String ptr, final Expression valueExpr) {

		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ArrayList<Statement> result = new ArrayList<>();

		final IdentifierExpression loopCtrExpr = new IdentifierExpression(ignoreLoc, loopCtr);
		final IdentifierExpression ptrExpr = new IdentifierExpression(ignoreLoc, ptr);

		final Expression currentPtr = doPointerArithmetic(IASTBinaryExpression.op_plus, ignoreLoc, ptrExpr,
				new RValue(loopCtrExpr, mExpressionTranslation.getCTypeOfPointerComponents()),
				new CPrimitive(CPrimitives.VOID));
		for (final HeapDataArray hda : heapDataArrays) {
			final Expression convertedValue;
			final ExpressionResult exprRes =
					new ExpressionResult(new RValue(valueExpr, new CPrimitive(CPrimitives.UCHAR)));
			if (hda.getName().equals(SFO.POINTER)) {
				mExpressionTranslation.convertIntToInt(ignoreLoc, exprRes,
						mExpressionTranslation.getCTypeOfPointerComponents());
				final Expression zero = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
						mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
				convertedValue = constructPointerFromBaseAndOffset(zero, exprRes.lrVal.getValue(), ignoreLoc);
			} else {
				// convert to smallest
				final List<ReadWriteDefinition> rwds =
						mMemoryModel.getReadWriteDefinitionForHeapDataArray(hda, getRequiredMemoryModelFeatures());
				// PRIMITIVE primitive = getCprimitiveThatFitsBest(rwds);
				final CPrimitives primitive = getCprimitiveThatFitsBest(hda.getSize());
				mExpressionTranslation.convertIntToInt(ignoreLoc, exprRes, new CPrimitive(primitive));
				convertedValue = exprRes.lrVal.getValue();
			}
			final String memArrayName = hda.getVariableName();
			final ArrayLHS destAcc =
					new ArrayLHS(ignoreLoc, new VariableLHS(ignoreLoc, memArrayName), new Expression[] { currentPtr });

			result.add(new AssignmentStatement(ignoreLoc, new LeftHandSide[] { destAcc },
					new Expression[] { convertedValue }));
		}
		return result;
	}

	/**
	 * Returns an CPrimitive which is unsigned, integer and not bool that has the smallest bytesize.
	 */
	private CPrimitives getCprimitiveThatFitsBest(final List<ReadWriteDefinition> test) {
		int smallestBytesize = Integer.MAX_VALUE;
		for (final ReadWriteDefinition rwd : test) {
			if (rwd.getBytesize() < smallestBytesize) {
				smallestBytesize = rwd.getBytesize();
			}
		}
		if (smallestBytesize == 0) {
			// we only have unbounded data types
			return CPrimitives.UCHAR;
		}
		for (final CPrimitives primitive : new CPrimitives[] { CPrimitives.UCHAR, CPrimitives.USHORT, CPrimitives.UINT,
				CPrimitives.ULONG, CPrimitives.ULONGLONG }) {
			if (mTypeSizes.getSize(primitive) == smallestBytesize) {
				return primitive;
			}
		}
		throw new AssertionError("don't know how to store value on heap");
	}

	/**
	 * Returns an CPrimitive which is unsigned, integer and not bool that has the smallest bytesize.
	 */
	private CPrimitives getCprimitiveThatFitsBest(final int byteSize) {
		if (byteSize == 0) {
			// we only have unbounded data types
			return CPrimitives.UCHAR;
		}
		for (final CPrimitives primitive : new CPrimitives[] { CPrimitives.UCHAR, CPrimitives.USHORT, CPrimitives.UINT,
				CPrimitives.ULONG, CPrimitives.ULONGLONG }) {
			if (mTypeSizes.getSize(primitive) == byteSize) {
				return primitive;
			}
		}
		throw new AssertionError("don't know how to store value on heap");
	}

	/**
	 * Construct specification and implementation for our Boogie representation of the memset function defined in
	 * 7.24.6.1 of C11. void *memset(void *s, int c, size_t n);
	 * 
	 * @param main
	 * @param heapDataArrays
	 * @return
	 */
	private List<Declaration> declareMemset(final Dispatcher main, final Collection<HeapDataArray> heapDataArrays) {
		final ArrayList<Declaration> decls = new ArrayList<>();
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();

		final String inParamPtr = "#ptr";
		final String inParamValue = "#value";
		final String inParamAmount = "#amount";
		final String outParamResult = "#res";
		final String proc = MemoryModelDeclarations.C_Memset.getName();

		final VarList inParamPtrVl =
				new VarList(ignoreLoc, new String[] { inParamPtr }, mTypeHandler.constructPointerType(ignoreLoc));
		final VarList inParamValueVl = new VarList(ignoreLoc, new String[] { inParamValue },
				mTypeHandler.cType2AstType(ignoreLoc, new CPrimitive(CPrimitives.INT)));
		final VarList inParamAmountVl = new VarList(ignoreLoc, new String[] { inParamAmount },
				mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSize_T()));
		final VarList outParamResultVl =
				new VarList(ignoreLoc, new String[] { outParamResult }, mTypeHandler.constructPointerType(ignoreLoc));

		final VarList[] inParams = new VarList[] { inParamPtrVl, inParamValueVl, inParamAmountVl };
		final VarList[] outParams = new VarList[] { outParamResultVl };

		final List<VariableDeclaration> decl = new ArrayList<>();
		final CPrimitive sizeT = mTypeSizeAndOffsetComputer.getSize_T();
		final String loopCtr = mNameHandler.getTempVarUID(SFO.AUXVAR.LOOPCTR, sizeT);
		final ASTType astType = mTypeHandler.cType2AstType(ignoreLoc, sizeT);
		final VarList lcvl = new VarList(ignoreLoc, new String[] { loopCtr }, astType);
		final VariableDeclaration loopCtrDec =
				new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { lcvl });
		decl.add(loopCtrDec);

		// converted value to unsigned char
		final IdentifierExpression inParamValueExpr = new IdentifierExpression(ignoreLoc, inParamValue);
		final ExpressionResult exprRes =
				new ExpressionResult(new RValue(inParamValueExpr, new CPrimitive(CPrimitives.INT)));
		mExpressionTranslation.convertIntToInt(ignoreLoc, exprRes, new CPrimitive(CPrimitives.UCHAR));
		final Expression convertedValue = exprRes.lrVal.getValue();

		final List<Statement> loopBody = constructMemsetLoopBody(heapDataArrays, loopCtr, inParamPtr, convertedValue);

		final Expression one = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
				mTypeSizeAndOffsetComputer.getSize_T(), BigInteger.ONE);
		final IdentifierExpression inParamAmountExpr = new IdentifierExpression(ignoreLoc, inParamAmount);
		final List<Statement> stmt = constructCountingLoop(inParamAmountExpr, loopCtr, one, loopBody);

		final Body procBody = new Body(ignoreLoc, decl.toArray(new VariableDeclaration[decl.size()]),
				stmt.toArray(new Statement[stmt.size()]));

		// make the specifications
		final ArrayList<Specification> specs = new ArrayList<>();

		// add modifies spec
		final ModifiesSpecification modifiesSpec = announceModifiedGlobals(proc, heapDataArrays);
		specs.add(modifiesSpec);

		// add requires #valid[#ptr!base];
		addPointerBaseValidityCheck(ignoreLoc, inParamPtr, specs);

		// add requires (#size + #ptr!offset <= #length[#ptr!base] && 0 <= #ptr!offset);
		checkPointerTargetFullyAllocated(ignoreLoc, inParamAmountExpr, inParamPtr, specs);

		// free ensures #res == dest;
		final EnsuresSpecification returnValue = new EnsuresSpecification(ignoreLoc, true,
				ExpressionFactory.newBinaryExpression(ignoreLoc, Operator.COMPEQ,
						new IdentifierExpression(ignoreLoc, outParamResult),
						new IdentifierExpression(ignoreLoc, inParamPtr)));
		specs.add(returnValue);

		// add the procedure declaration
		final Procedure procDecl = new Procedure(ignoreLoc, new Attribute[0], proc, new String[0], inParams, outParams,
				specs.toArray(new Specification[specs.size()]), null);
		decls.add(procDecl);

		// add the procedure implementation
		final Procedure procImpl =
				new Procedure(ignoreLoc, new Attribute[0], proc, new String[0], inParams, outParams, null, procBody);
		decls.add(procImpl);

		return decls;
	}

	/**
	 * Returns call to our memset procedure and announces that memset is required by our memory model.
	 */
	public CallStatement constructUltimateMemsetCall(final ILocation loc, final Expression pointer,
			final Expression value, final Expression amount, final String resVarId) {
		mRequiredMemoryModelFeatures.require(MemoryModelDeclarations.C_Memset);
		return new CallStatement(loc, false, new VariableLHS[] { new VariableLHS(loc, resVarId) },
				MemoryModelDeclarations.C_Memset.getName(), new Expression[] { pointer, value, amount });
	}

	private VariableDeclaration constructMemoryArrayDeclaration(final ILocation loc, final String typeName,
			final ASTType astType) {
		final ASTType memoryArrayType =
				new ArrayType(loc, new String[0], new ASTType[] { mTypeHandler.constructPointerType(loc) }, astType);
		final VarList varList = new VarList(loc, new String[] { SFO.MEMORY + "_" + typeName }, memoryArrayType);
		return new VariableDeclaration(loc, new Attribute[0], new VarList[] { varList });
	}

	private List<Declaration> constructWriteProcedures(final ILocation loc,
			final Collection<HeapDataArray> heapDataArrays, final HeapDataArray heapDataArray) {
		final List<Declaration> result = new ArrayList<>();
		for (final ReadWriteDefinition rda : mMemoryModel.getReadWriteDefinitionForHeapDataArray(heapDataArray,
				mRequiredMemoryModelFeatures)) {
			result.add(constructWriteProcedure(loc, heapDataArrays, heapDataArray, rda));
		}
		return result;
	}

	private List<Declaration> constructReadProcedures(final ILocation loc, final HeapDataArray heapDataArray) {
		final List<Declaration> result = new ArrayList<>();
		for (final ReadWriteDefinition rda : mMemoryModel.getReadWriteDefinitionForHeapDataArray(heapDataArray,
				mRequiredMemoryModelFeatures)) {
			result.add(constructReadProcedure(loc, heapDataArray, rda));
		}
		return result;
	}

	private Procedure constructWriteProcedure(final ILocation loc, final Collection<HeapDataArray> heapDataArrays,
			final HeapDataArray heapDataArray, final ReadWriteDefinition rda) {
		final String value = "#value";
		final ASTType valueAstType = rda.getASTType();
		final String inPtr = "#ptr";
		final String writtenTypeSize = "#sizeOfWrittenType";

		final ASTType sizetType = mTypeHandler.cType2AstType(loc, mTypeSizeAndOffsetComputer.getSize_T());
		final VarList[] inWrite = new VarList[] { new VarList(loc, new String[] { value }, valueAstType),
				new VarList(loc, new String[] { inPtr }, mTypeHandler.constructPointerType(loc)),
				new VarList(loc, new String[] { writtenTypeSize }, sizetType) };

		// specification for memory writes
		final ArrayList<Specification> swrite = new ArrayList<>();

		addPointerBaseValidityCheck(loc, inPtr, swrite);

		final Expression sizeWrite = new IdentifierExpression(loc, writtenTypeSize);
		checkPointerTargetFullyAllocated(loc, sizeWrite, inPtr, swrite);

		final ModifiesSpecification mod = constructModifiesSpecification(loc, heapDataArrays, x -> x.getVariableName());
		swrite.add(mod);
		if (rda.getBytesize() == heapDataArray.getSize()) {
			addWriteEnsuresSpecification(loc, heapDataArrays, heapDataArray, value, x -> x, inPtr, x -> x, swrite);
		} else if (rda.getBytesize() < heapDataArray.getSize()) {
			final Function<Expression, Expression> valueExtension =
					x -> mExpressionTranslation.signExtend(loc, x, rda.getBytesize() * 8, heapDataArray.getSize() * 8);
			addWriteEnsuresSpecification(loc, heapDataArrays, heapDataArray, value, valueExtension, inPtr, x -> x,
					swrite);
		} else {
			assert rda.getBytesize() % heapDataArray.getSize() == 0 : "incompatible sizes";
			for (int i = 0; i < rda.getBytesize() / heapDataArray.getSize(); i++) {
				final Function<Expression, Expression> extractBits;
				final int currentI = i;
				extractBits = x -> mExpressionTranslation.extractBits(loc, x,
						heapDataArray.getSize() * (currentI + 1) * 8, heapDataArray.getSize() * currentI * 8);
				if (i == 0) {
					addWriteEnsuresSpecification(loc, heapDataArrays, heapDataArray, value, extractBits, inPtr, x -> x,
							swrite);
				} else {
					final BigInteger additionalOffset = BigInteger.valueOf(i * heapDataArray.getSize());
					final Function<Expression, Expression> pointerAddition =
							x -> addIntegerConstantToPointer(loc, x, additionalOffset);
					addWriteEnsuresSpecification(loc, heapDataArrays, heapDataArray, value, extractBits, inPtr,
							pointerAddition, swrite);
				}
			}
		}

		final Procedure result = new Procedure(loc, new Attribute[0], rda.getWriteProcedureName(), new String[0],
				inWrite, new VarList[0], swrite.toArray(new Specification[swrite.size()]), null);
		return result;
	}

	private static void addWriteEnsuresSpecification(final ILocation loc,
			final Collection<HeapDataArray> heapDataArrays, final HeapDataArray heapDataArray, final String value,
			final Function<Expression, Expression> valueModification, final String inPtr,
			final Function<Expression, Expression> ptrModification, final ArrayList<Specification> swrite) {
		for (final HeapDataArray other : heapDataArrays) {
			if (heapDataArray == other) {
				swrite.add(ensuresHeapArrayUpdate(loc, value, valueModification, inPtr, ptrModification, other));
			} else {
				swrite.add(ensuresHeapArrayHardlyModified(loc, inPtr, ptrModification, other));
			}

		}
	}

	private Procedure constructReadProcedure(final ILocation loc, final HeapDataArray hda,
			final ReadWriteDefinition rda) {
		// specification for memory reads
		final String value = "#value";
		final ASTType valueAstType = rda.getASTType();
		final String ptrId = "#ptr";
		final String readTypeSize = "#sizeOfReadType";
		final ASTType sizetType = mTypeHandler.cType2AstType(loc, mTypeSizeAndOffsetComputer.getSize_T());
		final VarList[] inRead =
				new VarList[] { new VarList(loc, new String[] { ptrId }, mTypeHandler.constructPointerType(loc)),
						new VarList(loc, new String[] { readTypeSize }, sizetType) };
		final VarList[] outRead = new VarList[] { new VarList(loc, new String[] { value }, valueAstType) };

		final ArrayList<Specification> sread = new ArrayList<>();

		addPointerBaseValidityCheck(loc, ptrId, sread);

		final Expression sizeRead = new IdentifierExpression(loc, readTypeSize);
		checkPointerTargetFullyAllocated(loc, sizeRead, ptrId, sread);

		final Expression arr = new IdentifierExpression(loc, hda.getVariableName());
		final Expression ptrExpr = new IdentifierExpression(loc, ptrId);

		final Expression dataFromHeap;
		if (rda.getBytesize() == hda.getSize()) {
			dataFromHeap = constructOneDimensionalArrayAccess(loc, arr, ptrExpr);
		} else if (rda.getBytesize() < hda.getSize()) {
			dataFromHeap = mExpressionTranslation.extractBits(loc,
					constructOneDimensionalArrayAccess(loc, arr, ptrExpr), rda.getBytesize() * 8, 0);
		} else {
			assert rda.getBytesize() % hda.getSize() == 0 : "incompatible sizes";
			final Expression[] dataChunks = new Expression[rda.getBytesize() / hda.getSize()];
			for (int i = 0; i < dataChunks.length; i++) {
				if (i == 0) {
					dataChunks[dataChunks.length - 1 - 0] = constructOneDimensionalArrayAccess(loc, arr, ptrExpr);
				} else {
					final Expression index =
							addIntegerConstantToPointer(loc, ptrExpr, BigInteger.valueOf(i * hda.getSize()));
					dataChunks[dataChunks.length - 1 - i] = constructOneDimensionalArrayAccess(loc, arr, index);
				}
			}
			dataFromHeap = mExpressionTranslation.concatBits(loc, Arrays.asList(dataChunks), hda.getSize());
		}
		final Expression valueExpr = new IdentifierExpression(loc, value);
		final Expression equality =
				ExpressionFactory.newBinaryExpression(loc, Operator.COMPEQ, valueExpr, dataFromHeap);
		sread.add(new EnsuresSpecification(loc, false, equality));
		final Procedure result = new Procedure(loc, new Attribute[0], rda.getReadProcedureName(), new String[0], inRead,
				outRead, sread.toArray(new Specification[sread.size()]), null);
		return result;
	}

	private Expression addIntegerConstantToPointer(final ILocation loc, final Expression ptrExpr,
			final BigInteger integerConstant) {
		final Expression base = getPointerBaseAddress(ptrExpr, loc);
		final Expression offset = getPointerOffset(ptrExpr, loc);
		final Expression addition = mExpressionTranslation.constructLiteralForIntegerType(loc,
				mTypeSizeAndOffsetComputer.getSize_T(), integerConstant);
		final Expression offsetPlus =
				mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_plus, offset,
						mTypeSizeAndOffsetComputer.getSize_T(), addition, mTypeSizeAndOffsetComputer.getSize_T());
		return constructPointerFromBaseAndOffset(base, offsetPlus, loc);
	}

	private static Expression constructOneDimensionalArrayAccess(final ILocation loc, final Expression arr,
			final Expression index) {
		final Expression[] singletonIndex = new Expression[] { index };
		return new ArrayAccessExpression(loc, arr, singletonIndex);
	}

	private static Expression constructOneDimensionalArrayStore(final ILocation loc, final Expression arr,
			final Expression index, final Expression newValue) {
		final Expression[] singletonIndex = new Expression[] { index };
		return new ArrayStoreExpression(loc, arr, singletonIndex, newValue);
	}

	/**
	 * Construct a Boogie statement of the following form. arrayIdentifier[index] := value; TODO 2017-01-07 Matthias:
	 * This method is not directly related to the MemoryHandler and should probably moved to a some class for utility
	 * functions.
	 */
	public static AssignmentStatement constructOneDimensionalArrayUpdate(final ILocation loc, final Expression index,
			final String arrayIdentifier, final Expression value) {
		final LeftHandSide[] lhs = new LeftHandSide[] {
				new ArrayLHS(loc, new VariableLHS(loc, arrayIdentifier), new Expression[] { index }) };
		final Expression[] rhs = new Expression[] { value };
		final AssignmentStatement assignment = new AssignmentStatement(loc, lhs, rhs);
		return assignment;
	}

	// ensures #memory_X == old(#memory_X)[#ptr := #value];
	private static EnsuresSpecification ensuresHeapArrayUpdate(final ILocation loc, final String valueId,
			final Function<Expression, Expression> valueModification, final String ptrId,
			final Function<Expression, Expression> ptrModification, final HeapDataArray hda) {
		final Expression valueExpr = new IdentifierExpression(loc, valueId);
		final Expression memArray = new IdentifierExpression(loc, hda.getVariableName());
		final Expression ptrExpr = new IdentifierExpression(loc, ptrId);
		return ensuresArrayUpdate(loc, valueModification.apply(valueExpr), ptrModification.apply(ptrExpr), memArray);
	}

	// #memory_$Pointer$ == old(#memory_X)[#ptr := #memory_X[#ptr]];
	private static EnsuresSpecification ensuresHeapArrayHardlyModified(final ILocation loc, final String ptrId,
			final Function<Expression, Expression> ptrModification, final HeapDataArray hda) {
		final Expression memArray = new IdentifierExpression(loc, hda.getVariableName());
		final Expression ptrExpr = new IdentifierExpression(loc, ptrId);
		final Expression aae = constructOneDimensionalArrayAccess(loc, memArray, ptrExpr);
		return ensuresArrayUpdate(loc, aae, ptrModification.apply(ptrExpr), memArray);
	}

	private static EnsuresSpecification ensuresArrayUpdate(final ILocation loc, final Expression newValue,
			final Expression index, final Expression arrayExpr) {
		final Expression oldArray = ExpressionFactory.newUnaryExpression(loc, UnaryExpression.Operator.OLD, arrayExpr);
		final Expression ase = constructOneDimensionalArrayStore(loc, oldArray, index, newValue);
		final Expression eq = ExpressionFactory.newBinaryExpression(loc, Operator.COMPEQ, arrayExpr, ase);
		return new EnsuresSpecification(loc, false, eq);
	}

	/**
	 * 
	 * @param loc
	 *            location of translation unit
	 * @param vars
	 * @return ModifiesSpecification which says that all variables of the set vars can be modified.
	 */
	private static <T> ModifiesSpecification constructModifiesSpecification(final ILocation loc,
			final Collection<T> vars, final Function<T, String> varToString) {
		final VariableLHS[] modifie = new VariableLHS[vars.size()];
		int i = 0;
		for (final T variable : vars) {
			modifie[i] = new VariableLHS(loc, varToString.apply(variable));
			i++;
		}
		return new ModifiesSpecification(loc, false, modifie);
	}

	/**
	 * Add specification that target of pointer is fully allocated to the list {@link specList}. The specification
	 * checks that the address of the pointer plus the size of the type that we read/write is smaller than or equal to
	 * the size of the allocated memory at the base address of the pointer. Furthermore, we check that the offset is
	 * greater than or equal to zero. * In case mPointerBaseValidity is ASSERTandASSUME, we add the requires
	 * specification <code>requires (#size + #ptr!offset <= #length[#ptr!base] && 0 <= #ptr!offset)</code>. In case
	 * mPointerBaseValidity is ASSERTandASSUME, we add the <b>free</b> requires specification
	 * <code>free requires (#size + #ptr!offset <= #length[#ptr!base] && 0 <= #ptr!offset)</code>. In case
	 * mPointerBaseValidity is IGNORE, we add nothing.
	 * 
	 * @param loc
	 *            location of translation unit
	 * @param size
	 *            Expression that represents the size of the data type that we read/write at the address of the pointer.
	 * @param ptrName
	 *            name of pointer whose base address is checked
	 * @param specList
	 *            list to which the specification is added
	 */
	private void checkPointerTargetFullyAllocated(final ILocation loc, final Expression size, final String ptrName,
			final ArrayList<Specification> specList) {
		if (mPointerTargetFullyAllocated == PointerCheckMode.IGNORE) {
			// add nothing
			return;
		}
		final Expression leq;
		{
			final Expression ptrExpr = new IdentifierExpression(loc, ptrName);
			final Expression ptrBase = getPointerBaseAddress(ptrExpr, loc);
			final Expression aae = new ArrayAccessExpression(loc, getLengthArray(loc), new Expression[] { ptrBase });
			final Expression ptrOffset = getPointerOffset(ptrExpr, loc);
			final Expression sum = constructPointerComponentAddition(loc, size, ptrOffset);
			leq = constructPointerComponentLessEqual(loc, sum, aae);
		}
		final Expression offsetGeqZero;
		{
			final Expression ptrExpr = new IdentifierExpression(loc, ptrName);
			final Expression ptrOffset = getPointerOffset(ptrExpr, loc);
			final Expression nr0 = mExpressionTranslation.constructLiteralForIntegerType(loc,
					mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
			offsetGeqZero = constructPointerComponentLessEqual(loc, nr0, ptrOffset);

		}
		final Expression offsetInAllocatedRange =
				ExpressionFactory.newBinaryExpression(loc, BinaryExpression.Operator.LOGICAND, leq, offsetGeqZero);
		final boolean isFreeRequires;
		if (mPointerTargetFullyAllocated == PointerCheckMode.ASSERTandASSUME) {
			isFreeRequires = false;
		} else {
			assert mPointerTargetFullyAllocated == PointerCheckMode.ASSUME;
			isFreeRequires = true;
		}
		final RequiresSpecification spec = new RequiresSpecification(loc, isFreeRequires, offsetInAllocatedRange);
		final Check check = new Check(Spec.MEMORY_DEREFERENCE);
		check.annotate(spec);
		specList.add(spec);
	}

	/**
	 * Add specification that the pointer base address is valid to the list {@link specList}. In case
	 * mPointerBaseValidity is ASSERTandASSUME, we add the requires specification
	 * <code>requires #valid[#ptr!base]</code>. In case mPointerBaseValidity is ASSERTandASSUME, we add the <b>free</b>
	 * requires specification <code>free requires #valid[#ptr!base]</code>. In case mPointerBaseValidity is IGNORE, we
	 * add nothing.
	 * 
	 * @param loc
	 *            location of translation unit
	 * @param ptrName
	 *            name of pointer whose base address is checked
	 * @param specList
	 *            list to which the specification is added
	 */
	private void addPointerBaseValidityCheck(final ILocation loc, final String ptrName,
			final ArrayList<Specification> specList) {
		if (mPointerBaseValidity == PointerCheckMode.IGNORE) {
			// add nothing
			return;
		}
		final Expression ptrExpr = new IdentifierExpression(loc, ptrName);
		final Expression isValid = constructPointerBaseValidityCheck(loc, ptrExpr);
		final boolean isFreeRequires;
		if (mPointerBaseValidity == PointerCheckMode.ASSERTandASSUME) {
			isFreeRequires = false;
		} else {
			assert mPointerBaseValidity == PointerCheckMode.ASSUME;
			isFreeRequires = true;
		}
		final RequiresSpecification spec = new RequiresSpecification(loc, isFreeRequires, isValid);
		final Check check = new Check(Spec.MEMORY_DEREFERENCE);
		check.annotate(spec);
		specList.add(spec);
	}

	/**
	 * Construct expression that states that the base address of ptr is valid. Depending on the settings this expression
	 * is one of the following
	 * <ul>
	 * <li>#valid[#ptr!base]
	 * <li>#valid[#ptr!base] == 1
	 * <li>#valid[#ptr!base] == 1bv1
	 * </ul>
	 * 
	 * 
	 * 
	 */
	public Expression constructPointerBaseValidityCheck(final ILocation loc, final Expression ptr) {
		final Expression ptrBase = getPointerBaseAddress(ptr, loc);
		final ArrayAccessExpression aae =
				new ArrayAccessExpression(loc, getValidArray(loc), new Expression[] { ptrBase });
		final Expression isValid = mBooleanArrayHelper.compareWithTrue(aae);
		return isValid;
	}

	/**
	 * @param loc
	 *            location of translation unit
	 * @return new IdentifierExpression that represents the <em>#length array</em>
	 */
	public Expression getLengthArray(final ILocation loc) {
		getRequiredMemoryModelFeatures().require(MemoryModelDeclarations.Ultimate_Length);
		return new IdentifierExpression(loc, SFO.LENGTH);
	}

	/**
	 * @param loc
	 *            location of translation unit
	 * @return new IdentifierExpression that represents the <em>#valid array</em>
	 */
	public Expression getValidArray(final ILocation loc) {
		getRequiredMemoryModelFeatures().require(MemoryModelDeclarations.Ultimate_Valid);
		return new IdentifierExpression(loc, SFO.VALID);
	}

	private Expression constructPointerComponentAddition(final ILocation loc, final Expression left,
			final Expression right) {
		return mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_plus, left,
				mExpressionTranslation.getCTypeOfPointerComponents(), right,
				mExpressionTranslation.getCTypeOfPointerComponents());
	}

	private Expression constructPointerComponentLessEqual(final ILocation loc, final Expression left,
			final Expression right) {
		return mExpressionTranslation.constructBinaryComparisonExpression(loc, IASTBinaryExpression.op_lessEqual, left,
				mExpressionTranslation.getCTypeOfPointerComponents(), right,
				mExpressionTranslation.getCTypeOfPointerComponents());
	}

	/**
	 * Generate <code>procedure ~free(~addr:$Pointer$) returns()</code>'s declaration and implementation.
	 * 
	 * @param tuLoc
	 *            the location for the new nodes.
	 * @return declaration and implementation of procedure <code>~free</code>
	 */
	private ArrayList<Declaration> declareFree(final Dispatcher main, final ILocation tuLoc) {
		final ArrayList<Declaration> decl = new ArrayList<>();
		// procedure ~free(~addr:$Pointer$) returns();
		// requires #valid[~addr!base];
		// ensures #valid = old(valid)[~addr!base := false];
		// modifies #valid;
		final Expression nr0 = mExpressionTranslation.constructLiteralForIntegerType(tuLoc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
		final Expression addr = new IdentifierExpression(tuLoc, ADDR);
		final Expression valid = getValidArray(tuLoc);
		final Expression addrOffset = new StructAccessExpression(tuLoc, addr, SFO.POINTER_OFFSET);
		final Expression addrBase = new StructAccessExpression(tuLoc, addr, SFO.POINTER_BASE);
		final Expression[] idcFree = new Expression[] { addrBase };

		final ArrayList<Specification> specFree = new ArrayList<>();

		/*
		 * creating the specification according to C99:7.20.3.2-2: The free function causes the space pointed to by ptr
		 * to be deallocated, that is, made available for further allocation. If ptr is a null pointer, no action
		 * occurs. Otherwise, if the argument does not match a pointer earlier returned by the calloc, malloc, or
		 * realloc function, or if the space has been deallocated by a call to free or realloc, the behavior is
		 * undefined.
		 */
		final Check check = new Check(Spec.MEMORY_FREE);
		final boolean free = !mCheckFreeValid;
		final RequiresSpecification offsetZero = new RequiresSpecification(tuLoc, free,
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, addrOffset, nr0));
		check.annotate(offsetZero);
		specFree.add(offsetZero);

		// ~addr!base == 0
		final Expression ptrBaseZero = mExpressionTranslation.constructLiteralForIntegerType(tuLoc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
		final Expression isNullPtr =
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, addrBase, ptrBaseZero);

		// requires ~addr!base == 0 || #valid[~addr!base];
		final Expression addrIsValid =
				mBooleanArrayHelper.compareWithTrue(new ArrayAccessExpression(tuLoc, valid, idcFree));
		final RequiresSpecification baseValid = new RequiresSpecification(tuLoc, free,
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.LOGICOR, isNullPtr, addrIsValid));

		check.annotate(baseValid);
		specFree.add(baseValid);

		// ensures (if ~addr!base == 0 then #valid == old(#valid) else #valid == old(#valid)[~addr!base := false])
		final Expression bLFalse = mBooleanArrayHelper.constructFalse();
		final Expression updateValidArray = ExpressionFactory.newIfThenElseExpression(tuLoc, isNullPtr,
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, valid,
						ExpressionFactory.newUnaryExpression(tuLoc, UnaryExpression.Operator.OLD, valid)),
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, valid,
						new ArrayStoreExpression(tuLoc,
								ExpressionFactory.newUnaryExpression(tuLoc, UnaryExpression.Operator.OLD, valid),
								idcFree, bLFalse)));

		specFree.add(new EnsuresSpecification(tuLoc, free, updateValidArray));
		specFree.add(new ModifiesSpecification(tuLoc, false, new VariableLHS[] { new VariableLHS(tuLoc, SFO.VALID) }));

		decl.add(new Procedure(tuLoc, new Attribute[0], SFO.FREE, new String[0],
				new VarList[] { new VarList(tuLoc, new String[] { ADDR }, mTypeHandler.constructPointerType(tuLoc)) },
				new VarList[0], specFree.toArray(new Specification[specFree.size()]), null));

		if (ADD_IMPLEMENTATIONS) {
			// procedure ~free(~addr:$Pointer$) returns() {
			// #valid[~addr!base] := false;
			// // havoc #memory[n];
			// }
			final LeftHandSide[] lhs =
					new LeftHandSide[] { new ArrayLHS(tuLoc, new VariableLHS(tuLoc, SFO.VALID), idcFree) };
			final Expression[] rhsFree = new Expression[] { bLFalse };
			final Body bodyFree = new Body(tuLoc, new VariableDeclaration[0],
					new Statement[] { new AssignmentStatement(tuLoc, lhs, rhsFree) });
			decl.add(
					new Procedure(tuLoc,
							new Attribute[0], SFO.FREE, new String[0], new VarList[] { new VarList(tuLoc,
									new String[] { ADDR }, mTypeHandler.constructPointerType(tuLoc)) },
							new VarList[0], null, bodyFree));
		}
		return decl;
	}

	/**
	 * Generate <code>procedure ULTIMATE.dealloc(~addr:$Pointer$) returns()</code>'s declaration and implementation.
	 * This procedure should be used for deallocations where do not want to check if given memory area is valid (because
	 * we already know this) which is the case, e.g., for arrays that we store on the heap or for alloca.
	 * 
	 * @param tuLoc
	 *            the location for the new nodes.
	 * @return declaration and implementation of procedure <code>~free</code>
	 */
	private ArrayList<Declaration> declareDeallocation(final Dispatcher main, final ILocation tuLoc) {
		final ArrayList<Declaration> decl = new ArrayList<>();
		// ensures #valid = old(valid)[~addr!base := false];
		final Expression bLFalse = mBooleanArrayHelper.constructFalse();
		final Expression addr = new IdentifierExpression(tuLoc, ADDR);
		final Expression valid = getValidArray(tuLoc);
		final Expression addrBase = new StructAccessExpression(tuLoc, addr, SFO.POINTER_BASE);
		final Expression[] idcFree = new Expression[] { addrBase };

		final ArrayList<Specification> specFree = new ArrayList<>();

		final Expression updateValidArray = ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, valid,
				new ArrayStoreExpression(tuLoc,
						ExpressionFactory.newUnaryExpression(tuLoc, UnaryExpression.Operator.OLD, valid), idcFree,
						bLFalse));

		specFree.add(new EnsuresSpecification(tuLoc, true, updateValidArray));
		specFree.add(new ModifiesSpecification(tuLoc, false, new VariableLHS[] { new VariableLHS(tuLoc, SFO.VALID) }));

		decl.add(new Procedure(tuLoc, new Attribute[0], SFO.DEALLOC, new String[0],
				new VarList[] { new VarList(tuLoc, new String[] { ADDR }, mTypeHandler.constructPointerType(tuLoc)) },
				new VarList[0], specFree.toArray(new Specification[specFree.size()]), null));

		return decl;
	}

	/**
	 * Generate <code>procedure ~malloc(~size:int) returns (#res:$Pointer$);</code>'s declaration and implementation.
	 * 
	 * @param typeHandler
	 * 
	 * @param tuLoc
	 *            the location for the new nodes.
	 * @return declaration and implementation of procedure <code>~malloc</code>
	 */
	private ArrayList<Declaration> declareMalloc(final ITypeHandler typeHandler, final ILocation tuLoc) {
		final ASTType intType = typeHandler.cType2AstType(tuLoc, mExpressionTranslation.getCTypeOfPointerComponents());
		final Expression nr0 = mExpressionTranslation.constructLiteralForIntegerType(tuLoc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
		final Expression addr = new IdentifierExpression(tuLoc, ADDR);
		final Expression valid = getValidArray(tuLoc);
		final Expression addrOffset = new StructAccessExpression(tuLoc, addr, SFO.POINTER_OFFSET);
		final Expression addrBase = new StructAccessExpression(tuLoc, addr, SFO.POINTER_BASE);
		final ArrayList<Declaration> decl = new ArrayList<>();
		// procedure ~malloc(~size:int) returns (#res:$Pointer$);
		// requires ~size >= 0;
		// ensures old(#valid)[#res!base] = false;
		// ensures #valid = old(#valid)[#res!base := true];
		// ensures #res!offset == 0;
		// ensures #res!base != 0;
		// ensures #length = old(#length)[#res!base := ~size];
		// modifies #length, #valid;
		final Expression res = new IdentifierExpression(tuLoc, SFO.RES);
		final Expression length = getLengthArray(tuLoc);
		final Expression base = new StructAccessExpression(tuLoc, res, SFO.POINTER_BASE);
		final Expression[] idcMalloc = new Expression[] { base };
		final Expression bLTrue = mBooleanArrayHelper.constructTrue();
		final Expression bLFalse = mBooleanArrayHelper.constructFalse();
		final IdentifierExpression size = new IdentifierExpression(tuLoc, SIZE);
		final List<Specification> specMalloc = new ArrayList<>();

		specMalloc
				.add(new EnsuresSpecification(tuLoc, false,
						ExpressionFactory.newBinaryExpression(tuLoc,
								Operator.COMPEQ, new ArrayAccessExpression(tuLoc, ExpressionFactory
										.newUnaryExpression(tuLoc, UnaryExpression.Operator.OLD, valid), idcMalloc),
								bLFalse)));
		specMalloc.add(ensuresArrayUpdate(tuLoc, bLTrue, base, valid));
		specMalloc.add(new EnsuresSpecification(tuLoc, false, ExpressionFactory.newBinaryExpression(tuLoc,
				Operator.COMPEQ, new StructAccessExpression(tuLoc, res, SFO.POINTER_OFFSET), nr0)));
		specMalloc.add(new EnsuresSpecification(tuLoc, false, ExpressionFactory.newBinaryExpression(tuLoc,
				Operator.COMPNEQ, new StructAccessExpression(tuLoc, res, SFO.POINTER_BASE), nr0)));
		specMalloc.add(new EnsuresSpecification(tuLoc, false,
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, length,
						new ArrayStoreExpression(tuLoc,
								ExpressionFactory.newUnaryExpression(tuLoc, UnaryExpression.Operator.OLD, length),
								idcMalloc, size))));
		specMalloc.add(new ModifiesSpecification(tuLoc, false,
				new VariableLHS[] { new VariableLHS(tuLoc, SFO.VALID), new VariableLHS(tuLoc, SFO.LENGTH) }));
		decl.add(new Procedure(tuLoc, new Attribute[0], MemoryModelDeclarations.Ultimate_Alloc.getName(), new String[0],
				new VarList[] { new VarList(tuLoc, new String[] { SIZE }, intType) },
				new VarList[] { new VarList(tuLoc, new String[] { SFO.RES }, typeHandler.constructPointerType(tuLoc)) },
				specMalloc.toArray(new Specification[specMalloc.size()]), null));
		if (ADD_IMPLEMENTATIONS) {
			// procedure ~malloc(~size:int) returns (#res:pointer) {
			// var ~addr : pointer;
			//
			// assume ~addr!offset = 0;
			// assume ~addr!base != 0;
			// assume !#valid[~addr!base];
			// // #valid setzen
			// #valid = #valid[~addr!base := true];
			// #length = #length[~addr!base := size];
			// // return pointer
			// #res := ~addr;
			// }
			final Expression[] idcAddrBase = new Expression[] { addrBase };
			final VariableDeclaration[] localVars =
					new VariableDeclaration[] { new VariableDeclaration(tuLoc, new Attribute[0], new VarList[] {
							new VarList(tuLoc, new String[] { ADDR }, typeHandler.constructPointerType(tuLoc)) }) };
			final Statement[] block = new Statement[6];
			block[0] = new AssumeStatement(tuLoc,
					ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, addrOffset, nr0));
			block[1] = new AssumeStatement(tuLoc,
					ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPNEQ, addrBase, nr0));
			block[2] = new AssumeStatement(tuLoc, ExpressionFactory.newUnaryExpression(tuLoc,
					UnaryExpression.Operator.LOGICNEG, new ArrayAccessExpression(tuLoc, valid, idcAddrBase)));
			block[3] = new AssignmentStatement(tuLoc, new LeftHandSide[] { new VariableLHS(tuLoc, SFO.VALID) },
					new Expression[] { new ArrayStoreExpression(tuLoc, valid, idcAddrBase, bLTrue) });
			block[4] = new AssignmentStatement(tuLoc, new LeftHandSide[] { new VariableLHS(tuLoc, SFO.LENGTH) },
					new Expression[] { new ArrayStoreExpression(tuLoc, length, idcAddrBase, size) });
			block[5] = new AssignmentStatement(tuLoc, new LeftHandSide[] { new VariableLHS(tuLoc, SFO.RES) },
					new Expression[] { addr });
			final Body bodyMalloc = new Body(tuLoc, localVars, block);
			decl.add(new Procedure(tuLoc, new Attribute[0], MemoryModelDeclarations.Ultimate_Alloc.getName(),
					new String[0], new VarList[] { new VarList(tuLoc, new String[] { SIZE }, intType) },
					new VarList[] {
							new VarList(tuLoc, new String[] { SFO.RES }, typeHandler.constructPointerType(tuLoc)) },
					null, bodyMalloc));
		}
		return decl;
	}

	/**
	 * Creates a function call expression for the ~free(e) function!
	 * 
	 * @param main
	 *            a reference to the main dispatcher.
	 * @param fh
	 *            a reference to the FunctionHandler - required to add informations to the call graph.
	 * @param e
	 *            the expression referring to the pointer, that should be free'd.
	 * @param loc
	 *            Location for errors and new nodes in the AST.
	 * @return a function call expression for ~free(e).
	 */
	public CallStatement getFreeCall(final Dispatcher main, final FunctionHandler fh, final LRValue lrVal,
			final ILocation loc) {
		assert lrVal instanceof RValue || lrVal instanceof LocalLValue;
		getRequiredMemoryModelFeatures().require(MemoryModelDeclarations.Free);
		// assert lrVal.cType instanceof CPointer;//TODO -> must be a pointer or onHeap -- add a complicated assertion
		// or let it be??

		// Further checks are done in the precondition of ~free()!
		// ~free(E);
		final CallStatement freeCall =
				new CallStatement(loc, false, new VariableLHS[0], SFO.FREE, new Expression[] { lrVal.getValue() });
		// add required information to function handler.
		if (fh.getCurrentProcedureID() != null) {
			fh.addModifiedGlobal(SFO.FREE, SFO.VALID);
			fh.addCallGraphNode(SFO.FREE);
			fh.addCallGraphEdge(fh.getCurrentProcedureID(), SFO.FREE);
		}
		return freeCall;
	}

	/*
	 * 2015-11-07 Matthias: This is copy&paste from getFreeCall
	 */
	public CallStatement getDeallocCall(final Dispatcher main, final FunctionHandler fh, final LRValue lrVal,
			final ILocation loc) {
		assert lrVal instanceof RValue || lrVal instanceof LocalLValue;
		getRequiredMemoryModelFeatures().require(MemoryModelDeclarations.Free);
		// assert lrVal.cType instanceof CPointer;//TODO -> must be a pointer or onHeap -- add a complicated assertion
		// or let it be??

		// Further checks are done in the precondition of ~free()!
		// ~free(E);
		final CallStatement freeCall =
				new CallStatement(loc, false, new VariableLHS[0], SFO.DEALLOC, new Expression[] { lrVal.getValue() });
		// add required information to function handler.
		if (fh.getCurrentProcedureID() != null) {
			fh.addModifiedGlobal(SFO.DEALLOC, SFO.VALID);
			fh.addCallGraphNode(SFO.DEALLOC);
			fh.addCallGraphEdge(fh.getCurrentProcedureID(), SFO.DEALLOC);
		}
		return freeCall;
	}
	//
	// /**
	// * Creates a function call expression for the ~malloc(size) function!
	// *
	// * @param main
	// * a reference to the main dispatcher.
	// * @param fh
	// * a reference to the FunctionHandler - required to add
	// * information to the call graph.
	// * @param size
	// * the expression referring to size of the memory to be
	// * allocated.
	// * @param loc
	// * Location for errors and new nodes in the AST.
	// * @return a function call expression for ~malloc(size).
	// */
	// public ExpressionResult getMallocCall(Dispatcher main, FunctionHandler fh,
	// Expression size, ILocation loc) {
	// CPointer voidPointer = new CPointer(new CPrimitive(PRIMITIVE.VOID));
	// String tmpId = mNameHandler.getTempVarUID(SFO.AUXVAR.MALLOC, voidPointer);
	// VariableDeclaration tVarDecl = SFO.getTempVarVariableDeclaration(tmpId, mTypeHandler.constructPointerType(loc),
	// loc);
	//
	// LocalLValue llVal = new LocalLValue(new VariableLHS(loc, tmpId), voidPointer);
	// ExpressionResult mallocRex = new ExpressionResult(llVal);
	//
	// mallocRex.stmt.add(getMallocCall(main, fh, size, llVal, loc));
	// mallocRex.auxVars.put(tVarDecl, loc);
	// mallocRex.decl.add(tVarDecl);
	//
	// assert (CHandler.isAuxVarMapcomplete(main, mallocRex.decl, mallocRex.auxVars));
	// return mallocRex;
	// }

	public CallStatement getMallocCall(final LocalLValue resultPointer, final ILocation loc) {
		return getMallocCall(calculateSizeOf(loc, resultPointer.getCType()),
				((VariableLHS) resultPointer.getLHS()).getIdentifier(), loc);
	}

	public CallStatement getMallocCall(final Expression size, final String resultVarId, final ILocation loc) {
		mRequiredMemoryModelFeatures.require(MemoryModelDeclarations.Ultimate_Alloc);
		final CallStatement result =
				new CallStatement(loc, false, new VariableLHS[] { new VariableLHS(loc, resultVarId) },
						MemoryModelDeclarations.Ultimate_Alloc.getName(), new Expression[] { size });

		// add required information to function handler.
		if (mFunctionHandler.getCurrentProcedureID() != null) {
			mFunctionHandler.addModifiedGlobal(MemoryModelDeclarations.Ultimate_Alloc.getName(), SFO.VALID);
			mFunctionHandler.addModifiedGlobal(MemoryModelDeclarations.Ultimate_Alloc.getName(), SFO.LENGTH);

			mFunctionHandler.addCallGraphNode(MemoryModelDeclarations.Ultimate_Alloc.getName());
			mFunctionHandler.addCallGraphEdge(mFunctionHandler.getCurrentProcedureID(),
					MemoryModelDeclarations.Ultimate_Alloc.getName());
		}
		return result;
	}

	/**
	 * Generates a call of the read procedure and writes the returned value to a temp variable, returned in the
	 * expression of the returned ResultExpression. Note that we only read simple types from the heap -- when reading
	 * e.g. an array, we have to make readCalls for each cell.
	 * 
	 * @param tPointer
	 *            the address to read from.
	 * @param pointerCType
	 *            the CType of the pointer in tPointer
	 * 
	 * @return all declarations and statements required to perform the read, plus an identifierExpression holding the
	 *         read value.
	 */
	// 2015-10
	public ExpressionResult getReadCall(final Expression address, final CType resultType) {
		final ILocation loc = address.getLocation();
		// if (((CHandler) main.cHandler).getExpressionTranslation() instanceof BitvectorTranslation
		// && (resultType.getUnderlyingType() instanceof CPrimitive)) {
		// CPrimitive cPrimitive = (CPrimitive) resultType.getUnderlyingType();
		// if (main.getTypeSizes().getSize(cPrimitive.getType()) >
		// main.getTypeSizes().getSize(PRIMITIVE.INT)) {
		// throw new UnsupportedSyntaxException(loc,
		// "cannot read " + cPrimitive + " from heap");
		// }
		// }
		// boolean bitvectorConversionNeeded = (((CHandler) main.cHandler).getExpressionTranslation() instanceof
		// BitvectorTranslation
		// && (resultType.getUnderlyingType() instanceof CPrimitive)
		// && main.getTypeSizes().getSize(((CPrimitive) resultType.getUnderlyingType()).getType()) <
		// main.getTypeSizes().getSize(PRIMITIVE.INT));
		final boolean bitvectorConversionNeeded = false;

		final ArrayList<Statement> stmt = new ArrayList<>();
		final ArrayList<Declaration> decl = new ArrayList<>();
		final Map<VariableDeclaration, ILocation> auxVars = new LinkedHashMap<>();
		final ArrayList<Overapprox> overappr = new ArrayList<>();

		final String readCallProcedureName;
		{

			final CType ut;
			if (resultType instanceof CNamed) {
				ut = ((CNamed) resultType).getUnderlyingType();
			} else {
				ut = resultType;
			}

			if (ut instanceof CPrimitive) {
				final CPrimitive cp = (CPrimitive) ut;
				if (!SUPPORT_FLOATS_ON_HEAP && cp.isFloatingType()) {
					throw new UnsupportedSyntaxException(loc, FLOAT_ON_HEAP_UNSOUND_MESSAGE);
				}
				mRequiredMemoryModelFeatures.reportDataOnHeapRequired(cp.getType());
				readCallProcedureName = mMemoryModel.getReadProcedureName(cp.getType());
			} else if (ut instanceof CPointer) {
				mRequiredMemoryModelFeatures.reportPointerOnHeapRequired();
				readCallProcedureName = mMemoryModel.getReadPointerProcedureName();
			} else if (ut instanceof CNamed) {
				throw new AssertionError("we took underlying type");
			} else if (ut instanceof CArray) {
				// we assume it is an Array on Heap
				// assert main.cHandler.isHeapVar(((IdentifierExpression) lrVal.getValue()).getIdentifier());
				// but it may not only be on heap, because it is addressoffed, but also because it is inside
				// a struct that is addressoffed..
				mRequiredMemoryModelFeatures.reportPointerOnHeapRequired();
				readCallProcedureName = mMemoryModel.getReadPointerProcedureName();
			} else if (ut instanceof CEnum) {
				// enum is treated like an int
				mRequiredMemoryModelFeatures.reportDataOnHeapRequired(CPrimitives.INT);
				readCallProcedureName = mMemoryModel.getReadProcedureName(CPrimitives.INT);
			} else {
				throw new UnsupportedOperationException("unsupported type " + ut);
			}
		}

		final String tmpId = mNameHandler.getTempVarUID(SFO.AUXVAR.MEMREAD, resultType);
		final ASTType tmpAstType;
		if (bitvectorConversionNeeded) {
			tmpAstType = mTypeHandler.cType2AstType(loc, resultType);
		} else {
			tmpAstType = mTypeHandler.cType2AstType(loc, resultType);
		}
		final VariableDeclaration tVarDecl = SFO.getTempVarVariableDeclaration(tmpId, tmpAstType, loc);
		auxVars.put(tVarDecl, loc);
		decl.add(tVarDecl);
		final VariableLHS[] lhs = new VariableLHS[] { new VariableLHS(loc, tmpId) };
		final CallStatement call = new CallStatement(loc, false, lhs, readCallProcedureName, // heapType.toString(),
				new Expression[] { address, calculateSizeOf(loc, resultType) });
		for (final Overapprox overapprItem : overappr) {
			overapprItem.annotate(call);
		}
		stmt.add(call);
		assert CHandler.isAuxVarMapcomplete(mNameHandler, decl, auxVars);

		ExpressionResult result;
		if (bitvectorConversionNeeded) {
			result = new ExpressionResult(stmt, new RValue(new IdentifierExpression(loc, tmpId), resultType), decl,
					auxVars, overappr);
			mExpressionTranslation.convertIntToInt(loc, result, (CPrimitive) resultType.getUnderlyingType());
			final String bvtmpId = mNameHandler.getTempVarUID(SFO.AUXVAR.MEMREAD, resultType);
			final VariableDeclaration bvtVarDecl = SFO.getTempVarVariableDeclaration(bvtmpId, tmpAstType, loc);
			auxVars.put(bvtVarDecl, loc);
			decl.add(bvtVarDecl);
			final VariableLHS[] bvlhs = new VariableLHS[] { new VariableLHS(loc, bvtmpId) };
			final AssignmentStatement as =
					new AssignmentStatement(loc, bvlhs, new Expression[] { result.lrVal.getValue() });
			stmt.add(as);
			result.lrVal = new RValue(new IdentifierExpression(loc, bvtmpId), resultType);
		} else {
			result = new ExpressionResult(stmt, new RValue(new IdentifierExpression(loc, tmpId), resultType), decl,
					auxVars, overappr);
		}
		return result;
	}

	/**
	 * Generates a procedure call to writeT(val, ptr), writing val to the according memory array. (for the C-methode the
	 * argument order is value, target, for this method it's the other way around)
	 * 
	 * @param hlv
	 *            the HeapLvalue containing the address to write to
	 * @param rval
	 *            the value to write.
	 * 
	 * @return the required Statements to perform the write.
	 */
	public ArrayList<Statement> getWriteCall(final ILocation loc, final HeapLValue hlv, final Expression value,
			CType valueType) {
		// if (((CHandler) main.cHandler).getExpressionTranslation() instanceof BitvectorTranslation
		// && (valueType.getUnderlyingType() instanceof CPrimitive)) {
		// CPrimitive cPrimitive = (CPrimitive) valueType.getUnderlyingType();
		// if (main.getTypeSizes().getSize(cPrimitive.getType()) >
		// main.getTypeSizes().getSize(PRIMITIVE.INT)) {
		// throw new UnsupportedSyntaxException(loc,
		// "cannot write " + cPrimitive + " to heap");
		// }
		// }
		// boolean bitvectorConversionNeeded = (((CHandler) main.cHandler).getExpressionTranslation() instanceof
		// BitvectorTranslation
		// && (valueType.getUnderlyingType() instanceof CPrimitive)
		// && main.getTypeSizes().getSize(((CPrimitive) valueType.getUnderlyingType()).getType()) <
		// main.getTypeSizes().getSize(PRIMITIVE.INT));
		// if (bitvectorConversionNeeded) {
		// RValue tmpworkaroundrvalue = new RValue(value, valueType.getUnderlyingType(), false, false);
		// ExpressionResult tmpworkaround = new ExpressionResult(tmpworkaroundrvalue);
		// mExpressionTranslation.convertIntToInt(loc, tmpworkaround, new CPrimitive(PRIMITIVE.INT));
		// value = tmpworkaround.lrVal.getValue();
		// valueType = tmpworkaround.lrVal.getCType();
		// }

		final ArrayList<Statement> stmt = new ArrayList<>();

		if (valueType instanceof CNamed) {
			valueType = ((CNamed) valueType).getUnderlyingType();
		}

		if (valueType instanceof CPrimitive) {
			final CPrimitive cp = (CPrimitive) valueType;
			if (!SUPPORT_FLOATS_ON_HEAP && cp.isFloatingType()) {
				throw new UnsupportedSyntaxException(loc, FLOAT_ON_HEAP_UNSOUND_MESSAGE);
			}
			mRequiredMemoryModelFeatures.reportDataOnHeapRequired(cp.getType());
			final String writeCallProcedureName = mMemoryModel.getWriteProcedureName(cp.getType());
			final HeapDataArray dhp = mMemoryModel.getDataHeapArray(cp.getType());
			mFunctionHandler.addModifiedGlobal(mFunctionHandler.getCurrentProcedureID(), dhp.getVariableName());
			stmt.add(new CallStatement(loc, false, new VariableLHS[0], writeCallProcedureName,
					new Expression[] { value, hlv.getAddress(), calculateSizeOf(loc, hlv.getCType()) }));
		} else if (valueType instanceof CEnum) {
			// treat like INT
			mRequiredMemoryModelFeatures.reportDataOnHeapRequired(CPrimitives.INT);
			final String writeCallProcedureName = mMemoryModel.getWriteProcedureName(CPrimitives.INT);
			final HeapDataArray dhp = mMemoryModel.getDataHeapArray(CPrimitives.INT);
			mFunctionHandler.addModifiedGlobal(mFunctionHandler.getCurrentProcedureID(), dhp.getVariableName());
			stmt.add(new CallStatement(loc, false, new VariableLHS[0], writeCallProcedureName,
					new Expression[] { value, hlv.getAddress(), calculateSizeOf(loc, hlv.getCType()) }));
		} else if (valueType instanceof CPointer) {
			mRequiredMemoryModelFeatures.reportPointerOnHeapRequired();
			final String writeCallProcedureName = mMemoryModel.getWritePointerProcedureName();
			final HeapDataArray dhp = mMemoryModel.getPointerHeapArray();
			mFunctionHandler.addModifiedGlobal(mFunctionHandler.getCurrentProcedureID(), dhp.getVariableName());
			stmt.add(new CallStatement(loc, false, new VariableLHS[0], writeCallProcedureName,
					new Expression[] { value, hlv.getAddress(), calculateSizeOf(loc, hlv.getCType()) }));
		} else if (valueType instanceof CStruct) {
			final CStruct rStructType = (CStruct) valueType;
			for (final String fieldId : rStructType.getFieldIds()) {
				final Expression startAddress = hlv.getAddress();
				Expression newStartAddressBase = null;
				Expression newStartAddressOffset = null;
				if (startAddress instanceof StructConstructor) {
					newStartAddressBase = ((StructConstructor) startAddress).getFieldValues()[0];
					newStartAddressOffset = ((StructConstructor) startAddress).getFieldValues()[1];
				} else {
					newStartAddressBase = MemoryHandler.getPointerBaseAddress(startAddress, loc);
					newStartAddressOffset = MemoryHandler.getPointerOffset(startAddress, loc);
				}

				final CType fieldType = rStructType.getFieldType(fieldId);
				final StructAccessExpression sae = new StructAccessExpression(loc, value, fieldId);
				final Expression fieldOffset =
						mTypeSizeAndOffsetComputer.constructOffsetForField(loc, rStructType, fieldId);
				final Expression newOffset =
						mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_plus,
								newStartAddressOffset, mExpressionTranslation.getCTypeOfPointerComponents(),
								fieldOffset, mExpressionTranslation.getCTypeOfPointerComponents());
				final HeapLValue fieldHlv = new HeapLValue(
						constructPointerFromBaseAndOffset(newStartAddressBase, newOffset, loc), fieldType);
				stmt.addAll(getWriteCall(loc, fieldHlv, sae, fieldType));
			}

		} else if (valueType instanceof CArray) {

			final CArray arrayType = (CArray) valueType;
			final Expression arrayStartAddress = hlv.getAddress();
			Expression newStartAddressBase = null;
			Expression newStartAddressOffset = null;
			if (arrayStartAddress instanceof StructConstructor) {
				newStartAddressBase = ((StructConstructor) arrayStartAddress).getFieldValues()[0];
				newStartAddressOffset = ((StructConstructor) arrayStartAddress).getFieldValues()[1];
			} else {
				newStartAddressBase = MemoryHandler.getPointerBaseAddress(arrayStartAddress, loc);
				newStartAddressOffset = MemoryHandler.getPointerOffset(arrayStartAddress, loc);
			}

			final Expression valueTypeSize = calculateSizeOf(loc, arrayType.getValueType());

			Expression arrayEntryAddressOffset = newStartAddressOffset;

			// can we assume here, that we have a boogie array, right??
			if (arrayType.getDimensions().length == 1) {
				final BigInteger dimBigInteger =
						mExpressionTranslation.extractIntegerValue(arrayType.getDimensions()[0]);
				if (dimBigInteger == null) {
					throw new UnsupportedSyntaxException(loc,
							"variable length arrays not yet supported by this method");
				}
				final int dim = dimBigInteger.intValue();

				// Expression readArrayEntryAddressOffset = arrayType.isOnHeap() ? getPointerOffset(rval.getValue(),
				// loc) : null;

				for (int pos = 0; pos < dim; pos++) {
					RValue arrayAccRVal;
					// if (arrayType.isOnHeap()) {
					// arrayAccRVal = new RValue(
					// constructPointerFromBaseAndOffset(
					// getPointerBaseAddress(rval.getValue(), loc),
					// readArrayEntryAddressOffset, loc),
					// arrayType.getValueType());
					// readArrayEntryAddressOffset = CHandler.createArithmeticExpression(IASTBinaryExpression.op_plus,
					// readArrayEntryAddressOffset, valueTypeSize, loc);
					// } else {
					final Expression position = mExpressionTranslation.constructLiteralForIntegerType(loc,
							mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.valueOf(pos));
					arrayAccRVal = new RValue(new ArrayAccessExpression(loc, value, new Expression[] { position }),
							arrayType.getValueType());
					// }
					stmt.addAll(getWriteCall(loc,
							new HeapLValue(constructPointerFromBaseAndOffset(newStartAddressBase,
									arrayEntryAddressOffset, loc), arrayType.getValueType()),
							arrayAccRVal.getValue(), arrayAccRVal.getCType()));
					// TODO 2015-10-11 Matthias: Why is there an addition of value Type size
					// and no multiplication? Check this more carefully.
					arrayEntryAddressOffset =
							mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_plus,
									arrayEntryAddressOffset, mExpressionTranslation.getCTypeOfPointerComponents(),
									valueTypeSize, mExpressionTranslation.getCTypeOfPointerComponents());
				}
			} else {
				throw new UnsupportedSyntaxException(loc,
						"we need to generalize this to nested and/or variable length arrays");
			}

			// stmt.add(new CallStatement(loc, false, new VariableLHS[0], "write~" + SFO.POINTER,
			// new Expression[] { rval.getValue(), hlv.getAddress(), this.calculateSizeOf(hlv.cType, loc) }));
		} else {
			throw new UnsupportedSyntaxException(loc, "we don't recognize this type");
		}

		return stmt;
	}

	/**
	 * Takes a pointer Expression and returns the pointers base address. If it is already given as a struct, then the
	 * first field is returned, otherwise a StructAccessExpression pointer!base is returned.
	 * 
	 * @param pointer
	 */
	public static Expression getPointerBaseAddress(final Expression pointer, final ILocation loc) {
		if (pointer instanceof StructConstructor) {
			return ((StructConstructor) pointer).getFieldValues()[0];
		}
		return new StructAccessExpression(loc, pointer, "base");
	}

	/**
	 * Takes a pointer Expression and returns the pointers base address. If it is already given as a struct, then the
	 * second field is returned, otherwise a StructAccessExpression pointer!offset is returned.
	 * 
	 * @param pointer
	 */
	public static Expression getPointerOffset(final Expression pointer, final ILocation loc) {
		if (pointer instanceof StructConstructor) {
			return ((StructConstructor) pointer).getFieldValues()[1];
		}
		return new StructAccessExpression(loc, pointer, "offset");
	}

	public static StructConstructor constructPointerFromBaseAndOffset(final Expression base, final Expression offset,
			final ILocation loc) {
		return new StructConstructor(loc, new String[] { "base", "offset" }, new Expression[] { base, offset });
	}

	/**
	 * Takes a loop or function body and inserts mallocs and frees for all the identifiers in this.mallocedAuxPointers
	 */
	public ArrayList<Statement> insertMallocs(final Dispatcher main, final ArrayList<Statement> block) {
		final ArrayList<Statement> mallocs = new ArrayList<>();
		for (final LocalLValueILocationPair llvp : mVariablesToBeMalloced.currentScopeKeys()) {
			mallocs.add(this.getMallocCall(llvp.llv, llvp.loc));
		}
		final ArrayList<Statement> frees = new ArrayList<>();
		for (final LocalLValueILocationPair llvp : mVariablesToBeFreed.currentScopeKeys()) { // frees are inserted in
			// handleReturnStm
			frees.add(getDeallocCall(main, mFunctionHandler, llvp.llv, llvp.loc));
			frees.add(new HavocStatement(llvp.loc, new VariableLHS[] { (VariableLHS) llvp.llv.getLHS() }));
		}
		final ArrayList<Statement> newBlockAL = new ArrayList<>();
		newBlockAL.addAll(mallocs);
		newBlockAL.addAll(block);
		newBlockAL.addAll(frees);
		return newBlockAL;
	}

	public void addVariableToBeFreed(final Dispatcher main, final LocalLValueILocationPair llvp) {
		mVariablesToBeFreed.put(llvp, mVariablesToBeFreed.getActiveScopeNum());
	}

	public Map<LocalLValueILocationPair, Integer> getVariablesToBeMalloced() {
		return Collections.unmodifiableMap(mVariablesToBeMalloced);
	}

	public Map<LocalLValueILocationPair, Integer> getVariablesToBeFreed() {
		return Collections.unmodifiableMap(mVariablesToBeFreed);
	}

	public PointerCheckMode getPointerSubtractionAndComparisonValidityCheckMode() {
		return mCheckPointerSubtractionAndComparisonValidity;
	}

	public TypeSizeAndOffsetComputer getTypeSizeAndOffsetComputer() {
		return mTypeSizeAndOffsetComputer;
	}

	public IBooleanArrayHelper getBooleanArrayHelper() {
		return mBooleanArrayHelper;
	}

	/**
	 * Add or subtract a Pointer and an integer. Use this method only if you are sure that the type of the integer is
	 * the same as the type that we use for our pointer components. Otherwise, use the method below.
	 * 
	 * @param operator
	 *            Either plus or minus.
	 * @param integer
	 * @param valueType
	 *            The value type the pointer points to (we need it because we have to multiply with its size)
	 * 
	 * @return a pointer of the form: {base: ptr.base, offset: ptr.offset + integer * sizeof(valueType)}
	 */
	public Expression doPointerArithmetic(final int operator, final ILocation loc, final Expression ptrAddress,
			final RValue integer, final CType valueType) {
		if (mTypeSizes.getSize(((CPrimitive) integer.getCType()).getType()) != mTypeSizes
				.getSize(mExpressionTranslation.getCTypeOfPointerComponents().getType())) {
			throw new UnsupportedOperationException("not yet implemented, conversion is needed");
		}
		final Expression pointerBase = MemoryHandler.getPointerBaseAddress(ptrAddress, loc);
		final Expression pointerOffset = MemoryHandler.getPointerOffset(ptrAddress, loc);
		final Expression timesSizeOf = multiplyWithSizeOfAnotherType(loc, valueType, integer.getValue(),
				mExpressionTranslation.getCTypeOfPointerComponents());
		final Expression sum = mExpressionTranslation.constructArithmeticExpression(loc, operator, pointerOffset,
				mExpressionTranslation.getCTypeOfPointerComponents(), timesSizeOf,
				mExpressionTranslation.getCTypeOfPointerComponents());
		final StructConstructor newPointer = MemoryHandler.constructPointerFromBaseAndOffset(pointerBase, sum, loc);
		return newPointer;
	}

	/**
	 * Multiply an integerExpresion with the size of another type.
	 * 
	 * @param integerExpresionType
	 *            {@link CType} whose translation is the Boogie type of integerExpression and the result.
	 * @return An {@link Expression} that represents <i>integerExpression * sizeof(valueType)</i>
	 */
	public Expression multiplyWithSizeOfAnotherType(final ILocation loc, final CType valueType,
			final Expression integerExpression, final CPrimitive integerExpresionType) {
		final Expression timesSizeOf;
		timesSizeOf = mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_multiply,
				integerExpression, integerExpresionType, calculateSizeOf(loc, valueType), integerExpresionType);
		return timesSizeOf;
	}

	public interface IBooleanArrayHelper {
		ASTType constructBoolReplacementType();

		Expression constructTrue();

		Expression constructFalse();

		Expression compareWithTrue(Expression expr);
	}

	public static final class BooleanArrayHelper_Bool implements IBooleanArrayHelper {

		@Override
		public ASTType constructBoolReplacementType() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new PrimitiveType(ignoreLoc, "bool");
		}

		@Override
		public Expression constructTrue() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new BooleanLiteral(ignoreLoc, true);
		}

		@Override
		public Expression constructFalse() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new BooleanLiteral(ignoreLoc, false);
		}

		@Override
		public Expression compareWithTrue(final Expression expr) {
			return expr;
		}

	}

	public static final class BooleanArrayHelper_Integer implements IBooleanArrayHelper {

		@Override
		public ASTType constructBoolReplacementType() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new PrimitiveType(ignoreLoc, "int");
		}

		@Override
		public Expression constructTrue() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new IntegerLiteral(ignoreLoc, "1");
		}

		@Override
		public Expression constructFalse() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new IntegerLiteral(ignoreLoc, "0");
		}

		@Override
		public Expression compareWithTrue(final Expression expr) {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.newBinaryExpression(ignoreLoc, Operator.COMPEQ, expr, constructTrue());
		}

	}

	public static final class BooleanArrayHelper_Bitvector implements IBooleanArrayHelper {

		@Override
		public ASTType constructBoolReplacementType() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new PrimitiveType(ignoreLoc, "bv1");
		}

		@Override
		public Expression constructTrue() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new BitvecLiteral(ignoreLoc, "1", 1);
		}

		@Override
		public Expression constructFalse() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new BitvecLiteral(ignoreLoc, "0", 1);
		}

		@Override
		public Expression compareWithTrue(final Expression expr) {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.newBinaryExpression(ignoreLoc, Operator.COMPEQ, expr, constructTrue());
		}
	}

	public static final class RequiredMemoryModelFeatures {

		private final Set<CPrimitives> mDataOnHeapRequired = new HashSet<>();
		private boolean mPointerOnHeapRequired;
		private final Set<MemoryModelDeclarations> mRequiredMemoryModelDeclarations = new HashSet<>();

		public void reportPointerOnHeapRequired() {
			mPointerOnHeapRequired = true;
		}

		public void reportDataOnHeapRequired(final CPrimitives primitive) {
			mDataOnHeapRequired.add(primitive);
		}

		public boolean isPointerOnHeapRequired() {
			return mPointerOnHeapRequired;
		}

		public Set<CPrimitives> getDataOnHeapRequired() {
			return mDataOnHeapRequired;
		}

		public boolean isMemoryModelInfrastructureRequired() {
			return isPointerOnHeapRequired() || !getDataOnHeapRequired().isEmpty()
					|| !getRequiredMemoryModelDeclarations().isEmpty();
		}

		public boolean require(final MemoryModelDeclarations mmdecl) {
			return mRequiredMemoryModelDeclarations.add(mmdecl);
		}

		public Set<MemoryModelDeclarations> getRequiredMemoryModelDeclarations() {
			return Collections.unmodifiableSet(mRequiredMemoryModelDeclarations);
		}
	}

	public static enum MemoryModelDeclarations {
		//@formatter:off
		Ultimate_Alloc("#Ultimate.alloc"),
		Free(SFO.FREE),
		Ultimate_MemInit("#Ultimate.meminit"),
		C_Memcpy("#Ultimate.C_memcpy"),
		C_Memset("#Ultimate.C_memset"),
		Ultimate_Length("#length"),
		Ultimate_Valid("#valid"),
		//@formatter:on
		;

		private final String mName;

		MemoryModelDeclarations(final String name) {
			mName = name;
		}

		public String getName() {
			return mName;
		}
	}

	public void beginScope() {
		mVariablesToBeMalloced.beginScope();
		mVariablesToBeFreed.beginScope();
	}

	public void endScope() {
		mVariablesToBeMalloced.endScope();
		mVariablesToBeFreed.endScope();
	}

	/**
	 * Construct the statements that write a string literal on the heap. (According to 6.4.5 of C11) The first statement
	 * is a call that allocates the memory The preceding statements write the (integer) values of the string literal to
	 * the appropriate heap array. Finally we append 0, since string literals in C are null terminated. E.g., for the
	 * string literal "New" the result is the following.
	 * 
	 * call resultPointer := #Ultimate.alloc(value.length + 1); #memory_int[{ base: resultPointer!base, offset:
	 * resultPointer!offset + 0 }] := 78; #memory_int[{ base: resultPointer!base, offset: resultPointer!offset + 1 }] :=
	 * 101; #memory_int[{ base: resultPointer!base, offset: resultPointer!offset + 2 }] := 119; #memory_int[{ base:
	 * resultPointer!base, offset: resultPointer!offset + 3 }] := 0;
	 * 
	 * 2017-01-06 Matthias: This works for the our default memory model. I might not work for all our memory models.
	 * 
	 * @param writeValues
	 *            if not set we omit to write values and just allocate memory
	 */
	public List<Statement> writeStringToHeap(final ILocation loc, final String resultPointer, final char[] value,
			final boolean writeValues) {
		final Expression size = mExpressionTranslation.constructLiteralForIntegerType(loc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.valueOf(value.length + 1));
		final CallStatement ultimateAllocCall = getMallocCall(size, resultPointer, loc);
		final List<Statement> result = new ArrayList<>();
		result.add(ultimateAllocCall);
		if (writeValues) {
			for (int i = 0; i < value.length; i++) {
				final BigInteger valueBigInt = BigInteger.valueOf(value[i]);
				final AssignmentStatement statement = writeCharToHeap(loc, resultPointer, i, valueBigInt);
				result.add(statement);
			}
			// string literals are "nullterminated" i.e., suffixed by 0
			final AssignmentStatement statement = writeCharToHeap(loc, resultPointer, value.length, BigInteger.ZERO);
			result.add(statement);
		}
		return result;
	}

	private AssignmentStatement writeCharToHeap(final ILocation loc, final String resultPointer,
			final int additionalOffset, final BigInteger valueBigInt) {
		mRequiredMemoryModelFeatures.reportDataOnHeapRequired(CPrimitives.CHAR);
		final HeapDataArray dhp = mMemoryModel.getDataHeapArray(CPrimitives.CHAR);
		mFunctionHandler.addModifiedGlobal(mFunctionHandler.getCurrentProcedureID(), dhp.getVariableName());
		final String array = dhp.getVariableName();
		final Expression inputPointer = new IdentifierExpression(loc, resultPointer);
		final Expression additionalOffsetExpr = mExpressionTranslation.constructLiteralForIntegerType(loc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.valueOf(additionalOffset));
		final Expression pointer = doPointerArithmetic(IASTBinaryExpression.op_plus, loc, inputPointer,
				new RValue(additionalOffsetExpr, mExpressionTranslation.getCTypeOfPointerComponents()),
				new CPrimitive(CPrimitives.CHAR));
		final Expression valueExpr = mExpressionTranslation.constructLiteralForIntegerType(loc,
				new CPrimitive(CPrimitives.CHAR), valueBigInt);
		final AssignmentStatement statement = constructOneDimensionalArrayUpdate(loc, pointer, array, valueExpr);
		return statement;
	}

	public PointerCheckMode getPointerBaseValidityCheckMode() {
		return mPointerBaseValidity;
	}

	public PointerCheckMode getPointerTargetFullyAllocatedCheckMode() {
		return mPointerTargetFullyAllocated;
	}

}
