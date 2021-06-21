/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.truffle.api.matrix;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;


//TODO: this is all very boilerplate-y. 
//Can we add automate it with an annotation?
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(MatrixLibrary.class)
public class MatrixAdapter implements TruffleObject {

    Object adaptee;
    public MatrixAdapter(Object adaptee) {
        this.adaptee = adaptee;
    }

   public String toString() {
        Integer numRows = 0;
        Integer numCols = 0;
        Object adapteeType = 0;
        Object originLanguage = 0;
        String dimString = "<" + numRows + "," + numCols + ">";
        String containsString = "containing: " + adapteeType;
        String originString = "from: " + originLanguage;
        String repr = "MatrixAdapter - " + dimString + "- " + containsString + "- " + originString;
        return repr;
   }

    @ExportMessage Object removeAbstractions(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("removeAbstractions", arguments, interop);
    }

    @ExportMessage Object unwrap(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) {
        return adapter;
    }

    @ExportMessage
    public Object crossProduct(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("crossProduct", arguments, interop);
    }

    @ExportMessage
    public Object crossProductDuo(Object adapter, Object another, @CachedLibrary(limit = "3") InteropLibrary interop, @CachedLibrary(limit="10") MatrixLibrary tensorlib) throws UnsupportedMessageException {
        Object[] arguments = {adapter, another};
        return doCallObj("crossProductDuo", arguments, interop);
    }

    @ExportMessage
    public Object rowSum(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("rowSum", arguments, interop);
    }

    @ExportMessage
    public Object columnSum(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("columnSum", arguments, interop);

    }

    @ExportMessage
    public Double elementWiseSum(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallDouble("elementWiseSum", arguments, interop);
    }

    @ExportMessage
    public Object columnWiseAppend(Object adapter, Object tensor, @CachedLibrary(limit = "3") InteropLibrary interop, @CachedLibrary(limit="10") MatrixLibrary tensorlib) throws UnsupportedMessageException {
        Object[] arguments = {adapter, tensor};
        return doCallObj("columnWiseAppend", arguments, interop);
    }

    @ExportMessage
    public Object splice(Object adapter, Integer rowStart, Integer rowEnd, Integer colStart, Integer colEnd, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter, rowStart, rowEnd, colStart, colEnd};
        return doCallObj("splice", arguments, interop);
    }


    @ExportMessage
    public Object matrixAddition(Object adapter, Object otherMatrix, @CachedLibrary(limit = "3") InteropLibrary interop, @CachedLibrary(limit="10") MatrixLibrary tensorlib) throws UnsupportedMessageException{ 
        Object[] arguments = {adapter, otherMatrix};
        return doCallObj("matrixAddition", arguments, interop);
    }

    @ExportMessage
    public Object rowWiseAppend(Object adapter, Object tensor, @CachedLibrary(limit = "3") InteropLibrary interop, @CachedLibrary(limit="10") MatrixLibrary tensorlib) throws UnsupportedMessageException {
        Object[] arguments = {adapter, tensor};
        return doCallObj("rowWiseAppend", arguments, interop);
    }

    @ExportMessage
    public Object elementWiseExp(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("elementWiseExp", arguments, interop);
    }

    @ExportMessage
    public Object elementWiseSqrt(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("elementWiseSqrt", arguments, interop);
    }

    @ExportMessage
    public Object elementWiseLog(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("elementWiseLog", arguments, interop);
    }

    @ExportMessage
    public Object diagonal(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("diagonal", arguments, interop);
    }

    @ExportMessage
    public Object scalarAddition(Object adapter, Object scalar, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter, scalar};
        return doCallObj("scalarAddition", arguments, interop);
    }

    @ExportMessage
    public Object scalarMultiplication(Object adapter, Object scalar, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter, scalar};
        return doCallObj("scalarMultiplication", arguments, interop);
    }

    @ExportMessage
    public Integer getNumRows(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        
        return doCallInteger("getNumRows", arguments, interop);
    }

    @ExportMessage
    public Integer getNumCols(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException{
        Object[] arguments = {adapter};
        return doCallInteger("getNumCols", arguments, interop);
    }

    @ExportMessage
    public Object scalarExponentiation(Object adapter, Object scalar, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter, scalar};
        return doCallObj("scalarExponentiation", arguments, interop);
    }

    @ExportMessage
    public Object rightMatrixMultiplication(Object adapter, Object vector, @CachedLibrary(limit = "3") InteropLibrary interop, @CachedLibrary(limit = "10") MatrixLibrary tensorlib) throws UnsupportedMessageException {
        Object[] arguments = {adapter, vector};
        return doCallObj("rightMatrixMultiplication", arguments, interop);
    }

    @ExportMessage
    public Object leftMatrixMultiplication(Object adapter, Object vector, @CachedLibrary(limit = "3") InteropLibrary interop, @CachedLibrary(limit = "10") MatrixLibrary tensorlib) throws UnsupportedMessageException {
        Object[] arguments = {adapter, vector};
        return doCallObj("leftMatrixMultiplication", arguments, interop);
    }

    //TODO: Setting `limit= "3"` in here breaks LogReg. Why?
    @ExportMessage
    public Object transpose(Object adapter, @CachedLibrary(limit = "10") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("transpose", arguments, interop);
    }

    @ExportMessage
    public Object invertMatrix(Object adapter, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        Object[] arguments = {adapter};
        return doCallObj("invertMatrix", arguments, interop);
    }

    public Object doCallObj(String memberName, Object[] arguments, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        try {
           Object boundFunction = interop.readMember(adaptee, memberName);
           Object tensor = interop.execute(boundFunction, arguments);
           return tensor;
        }
        catch (ArityException | UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException exception) {
            System.out.println(exception.toString());
            throw UnsupportedMessageException.create();
        }
    }

    public Integer doCallInteger(String memberName, Object[] arguments, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        try {
            Object boundFunction = interop.readMember(adaptee, memberName);
            Object result = interop.execute(boundFunction, arguments);
            if(interop.isNumber(result) && interop.fitsInInt(result)) {
                return interop.asInt(result);
            }
            throw UnsupportedMessageException.create();
        }
        catch (ArityException | UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException exception) {
            throw UnsupportedMessageException.create();
        }
    }

    public Double doCallDouble(String memberName, Object[] arguments, @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException {
        try {
            Object boundFunction = interop.readMember(adaptee, memberName);
            Object result = interop.execute(boundFunction, arguments);
            if(interop.isNumber(result) && interop.fitsInDouble(result)) {
                return interop.asDouble(result);
            }
            throw UnsupportedMessageException.create();
        }
        catch (ArityException | UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException exception) {
            throw UnsupportedMessageException.create();
        }
    }

}
