package example;

final class ArrayBoundsIssues {

    int upperBound() {
        final int[] values = new int[2];
        return values[2];
    }

    int negativeIndex() {
        final int[] values = {1, 2};
        return values[-1];
    }

    int literalAllocation() {
        return (new int[1])[1];
    }

    int constantIndex() {
        final int length = 3;
        final int[] values = new int[length];
        return values[length];
    }

    int validIndex() {
        final int[] values = {1, 2};
        return values[1];
    }

    int unknownIndex(int index) {
        final int[] values = new int[2];
        return values[index];
    }

    int unknownLength(int length) {
        final int[] values = new int[length];
        return values[2];
    }

    int reassigned() {
        int[] values = new int[1];
        values = new int[3];
        return values[2];
    }
}
