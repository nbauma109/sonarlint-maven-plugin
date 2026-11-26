package example;

final class BrainExample {
    int calculate(int input) {
        int first = input;
        int second = input + 1;
        int third = input + 2;
        int fourth = input + 3;
        int fifth = input + 4;
        int sixth = input + 5;
        int total = 0;
        if (first > 0) {
            if (second > 0) {
                if (third > 0) {
                    total += fourth;
                }
            }
        }
        if (input > 0) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 1) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 2) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 3) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 4) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 5) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 6) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 7) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 8) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 9) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 10) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 11) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 12) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 13) {
            total += first;
            total += second;
            total += third;
        }
        if (input > 14) {
            total += first;
            total += second;
            total += third;
        }
        return total + fifth + sixth;
    }

    int simple(int input) {
        return input + 1;
    }
}
