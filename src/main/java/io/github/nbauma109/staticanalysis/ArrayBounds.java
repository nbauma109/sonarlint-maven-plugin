package io.github.nbauma109.staticanalysis;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.OptionalInt;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

/**
 * Reports only array accesses with statically established lengths and indices.
 */
final class ArrayBounds {

    private ArrayBounds() {
    }

    static boolean isOutOfBounds(Trees trees, TreePath path, ArrayAccessTree access) {
        OptionalInt length = arrayLength(trees, new TreePath(path, access.getExpression()));
        OptionalInt index = integerConstant(trees, new TreePath(path, access.getIndex()));
        if (length.isEmpty() || index.isEmpty() || length.getAsInt() < 0) {
            return false;
        }
        return index.getAsInt() < 0 || index.getAsInt() >= length.getAsInt();
    }

    private static OptionalInt arrayLength(Trees trees, TreePath expression) {
        TreePath path = unwrap(expression);
        if (path.getLeaf() instanceof NewArrayTree array) {
            return allocationLength(trees, path, array);
        }
        if (!(trees.getElement(path) instanceof VariableElement variable)
                || variable.getKind() != ElementKind.LOCAL_VARIABLE
                || !variable.getModifiers().contains(Modifier.FINAL)) {
            return OptionalInt.empty();
        }
        TreePath declaration = trees.getPath(variable);
        if (declaration == null || !(declaration.getLeaf() instanceof VariableTree local)
                || local.getInitializer() == null) {
            return OptionalInt.empty();
        }
        TreePath initializer = unwrap(new TreePath(declaration, local.getInitializer()));
        if (initializer.getLeaf() instanceof NewArrayTree array) {
            return allocationLength(trees, initializer, array);
        }
        return OptionalInt.empty();
    }

    private static OptionalInt allocationLength(Trees trees, TreePath path, NewArrayTree array) {
        if (array.getInitializers() != null) {
            return OptionalInt.of(array.getInitializers().size());
        }
        if (array.getDimensions().isEmpty()) {
            return OptionalInt.empty();
        }
        return integerConstant(trees, new TreePath(path, array.getDimensions().get(0)));
    }

    private static OptionalInt integerConstant(Trees trees, TreePath expression) {
        TreePath path = unwrap(expression);
        Tree tree = path.getLeaf();
        if (tree instanceof LiteralTree literal) {
            return integralValue(literal.getValue());
        }
        if (tree instanceof UnaryTree unary
                && (tree.getKind() == Tree.Kind.UNARY_MINUS || tree.getKind() == Tree.Kind.UNARY_PLUS)) {
            OptionalInt operand = integerConstant(trees, new TreePath(path, unary.getExpression()));
            if (operand.isPresent() && tree.getKind() == Tree.Kind.UNARY_MINUS) {
                return OptionalInt.of(-operand.getAsInt());
            }
            return operand;
        }
        if (trees.getElement(path) instanceof VariableElement variable) {
            return integralValue(variable.getConstantValue());
        }
        return OptionalInt.empty();
    }

    private static OptionalInt integralValue(Object value) {
        if (value instanceof Integer integer) {
            return OptionalInt.of(integer);
        }
        if (value instanceof Byte number) {
            return OptionalInt.of(number.intValue());
        }
        if (value instanceof Short number) {
            return OptionalInt.of(number.intValue());
        }
        if (value instanceof Character character) {
            return OptionalInt.of(character);
        }
        return OptionalInt.empty();
    }

    private static TreePath unwrap(TreePath expression) {
        TreePath path = expression;
        while (path.getLeaf() instanceof ParenthesizedTree parenthesized) {
            path = new TreePath(path, parenthesized.getExpression());
        }
        return path;
    }
}
