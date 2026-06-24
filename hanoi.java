// これは AI に書いてもらったハノイの塔の解法を示す Java プログラムです。
//
//


public class hanoi {
    public static void solveHanoi(int n, char from, char aux, char to) {
        if (n == 1) {
            System.out.println("Move disk 1 from " + from + " to " + to);
            return;
        }

        solveHanoi(n - 1, from, to, aux);
        System.out.println("Move disk " + n + " from " + from + " to " + to);
        solveHanoi(n - 1, aux, from, to);
    }

    public static void main(String[] args) {
        int n = 3; // サンプルとして 3 枚の円盤
        System.out.println("Tower of Hanoi with " + n + " disks:");
        solveHanoi(n, 'A', 'B', 'C');
    }
}
