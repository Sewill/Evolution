import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by savva on 01.07.2015.
 */
public class EM_Methods {
    /**
     * проверка ограничений
     */
    public static boolean checkedLimitation(Integer[] number, Integer[] number1, Integer[][] parametersLimitation) {
        boolean check = false;
        for (int i = 0; i < number.length; i++) {
            if (number[i] > parametersLimitation[1][i]+16 || number1[i] > parametersLimitation[1][i]+16) {
                check = true;
            }
            if (check) {
                break;
            }
        }

        return check;
    }

    /**
     * объединение параметров массива в одну строку
     */
    public static long[] combiningNumbers(Integer[][] mass, Integer[] numberOfBits) {

        long[] result = new long[mass.length];
        for (int i = 0; i < mass.length; i++) {
            Integer[] tempMas = mass[i];
            int resultLine = 0;
            for (int j = 0; j < tempMas.length; j++) {
                resultLine <<= numberOfBits[j];
                resultLine += tempMas[j];
            }

            result[i] = resultLine;
        }
        return result;
    }

    /**
     * разделение одной строки на отдельные параметры
     */
    public static Integer[] separationNumber(long number, Integer[] numberOfBits) {
        Integer[] result = new Integer[numberOfBits.length];
        long tempNumber = number;
        for (int i = 0; i < numberOfBits.length; i++) {
            int counter = numberOfBits[numberOfBits.length - i - 1];
            int mask = 0;
            for (int j = 0; j < counter; j++) {
                mask += 1;
                mask <<= 1;
            }
            Long temp = tempNumber & mask;
            result[numberOfBits.length - i - 1] = temp.intValue();
            tempNumber >>= counter;
        }
        return result;
    }

    /**
     * разрядность числа
     */
    public static int getDischarges(int i) {
        int counter = 0;

        for (; ; ) {
            if (i == 0) {
                break;
            }
            i = i >>> 1;
            counter++;
        }
        return counter;
    }

    /**
     * Вычисление точек разрыва
     *
     * @param numberOfBitsSum - разрядность числа
     */
    public static List<Integer> getBreakPoints(int numberOfBitsSum) {
        int countBreakPoints = 0;

        if (numberOfBitsSum > 1 && numberOfBitsSum < 6) {
            countBreakPoints = 1;
        } else if (numberOfBitsSum >= 6 && numberOfBitsSum < 12) {
            countBreakPoints = 2;
        } else if (numberOfBitsSum >= 12 && numberOfBitsSum < 18) {
            countBreakPoints = 3;
        } else if (numberOfBitsSum >= 18 && numberOfBitsSum < 24) {
            countBreakPoints = 4;
        } else if (numberOfBitsSum >= 24 && numberOfBitsSum < 30) {
            countBreakPoints = 5;
        } else if (numberOfBitsSum >= 30 && numberOfBitsSum <= 32) {
            countBreakPoints = 6;
        }


        Random rnd = new Random();
        int sizeU = numberOfBitsSum - 1 - 1;
        List<Integer> breakPoints = new ArrayList();
        int counter = 0;
        for (int i = countBreakPoints - 1; i >= 0; i--) {
            int x = sizeU - i * 2 - (counter);
            int y = rnd.nextInt(x + 1) + 1;
            counter += y;
            breakPoints.add(counter);
            counter++;
        }
        return breakPoints;
    }

    /**
     * метод кроссовенга
     */
    public static Long[] crossingOver(Integer numberOfBitsSum, long number1, long number2, List<Integer> breakPoints) {
        Long[] result = new Long[2];

        int mask1 = 0;
        int mask2 = 0;

        if (breakPoints.size() % 2 == 1) {
            breakPoints.add(numberOfBitsSum);
        }
        int counter1 = 0;
        for (int i = 0; i < breakPoints.size(); i = i + 2) {
            int tempBP1 = breakPoints.get(i) - counter1;
            for (int j = 0; j < tempBP1; j++) {
                mask1 <<= 1;
                mask1++;
            }

            mask1 <<= breakPoints.get(i + 1) - breakPoints.get(i);
            counter1 = breakPoints.get(i + 1);

            mask2 <<= breakPoints.get(i + 1) - breakPoints.get(i);
            int tempBP2 = breakPoints.get(i + 1) - breakPoints.get(i);
            for (int j = 0; j < tempBP2; j++) {
                mask2 <<= 1;
                mask2++;
            }
        }

        int temp = numberOfBitsSum - breakPoints.get(breakPoints.size() - 1);
        if (breakPoints.get(breakPoints.size() - 1) < numberOfBitsSum) {
            for (int j = 0; j < temp; j++) {
                mask1 <<= 1;
                mask1++;
            }

            mask2 <<= temp;
        }

        long correctNumber11 = number1 & mask2;
        long correctNumber12 = number2 & mask1;
        long correctNumber21 = number1 & mask1;
        long correctNumber22 = number2 & mask2;

        result[1] = (long) (correctNumber11) | (long) (correctNumber12);
        result[0] = (long) correctNumber21 | (long) correctNumber22;


        return result;
    }

    /**
     * метод осуществляющий оператор мутации с заданой вероятностью использования
     *
     * @param breakPoints     - точки разрыва
     * @param number          - само число с которым будем работать
     * @param numberOfBitsSum - максимальная размерность
     * @param min             - ограничение минимальной границы выборки
     * @param max             - ограничение максимально границы выборки
     */
    public static Long mutation(Integer numberOfBitsSum, Long number, List<Integer> breakPoints, int min, int max) {
        if (runner(min, max)) {

            int maskPublic = 0;

            int maxBreakPoint = 0;
            int masc1, masc2;
            List<Long> mas1 = new ArrayList();
            List<Long> mas2 = new ArrayList();

            int counter = 0;
            for (int i = 0; i < breakPoints.size(); i++) {
                int breakPoint = breakPoints.get(i);
                masc1 = 1 << numberOfBitsSum - breakPoint - 1;
                masc2 = 1 << numberOfBitsSum - breakPoint;

                if (breakPoint - counter > 1) {
                    if (maskPublic == 0) {
                        counter++;
                        maskPublic = maskPublic + 1;
                    }
                    int count = breakPoint - counter;
                    for (int j = 1; j < count; j++) {
                        maskPublic <<= 1;
                        maskPublic = maskPublic + 1;

                        counter++;
                    }
                    maskPublic <<= 2;
                    counter = counter + 2;
                } else {
                    maskPublic <<= 2;
                    counter = counter + 2;
                }
                maxBreakPoint = breakPoint;

                Long c1 = number & masc1;
                Long c2 = number & masc2;

                mas1.add(c1 << 1);
                mas2.add(c2 >> 1);
            }

            if (maxBreakPoint + 1 < numberOfBitsSum) {
                for (int j = 1; j < numberOfBitsSum - maxBreakPoint; j++) {
                    maskPublic <<= 1;
                    maskPublic = maskPublic + 1;
                }
            }

            Long numb = number;
            numb &= maskPublic;
            for (int i = 0; i < mas1.size(); i++) {
                numb = mas1.get(i) | numb;
                numb = mas2.get(i) | numb;
            }
            number = numb;
        } else {
        }
        return number;
    }

    /**
     * метод осуществляющий оператор инверсии с заданой вероятностью использования
     *
     * @param breakPoints     - точки разрыва
     * @param number          - само число с которым будем работать
     * @param numberOfBitsSum - максимальная размерность
     * @param min             - ограничение минимальной границы выборки
     * @param max             - ограничение максимально границы выборки
     */
    public static Long inversion(Integer numberOfBitsSum, Long number, List<Integer> breakPoints, int min, int max) {
        if (runner(min, max)) {
            int sizeBP = breakPoints.size();
            if (sizeBP % 2 == 1) {
                breakPoints.add(numberOfBitsSum);
            }

            for (int i = 0; i < sizeBP; i++) {
                int minBP = breakPoints.get(i);
                int maxBP = breakPoints.get(i + 1);
                int div = maxBP - minBP;
                int divC = div % 2 == 1 ? 1 : 1;
                long[] mass = new long[div];
                int divD = div / 2;
                int maskT = 1 << numberOfBitsSum - minBP - 1;
                for (int j = 0; j < div; j++) {
                    mass[j] = maskT & number;
                    if (j < divD) {
                        mass[j] = mass[j] >> div - 2 * j - divC;
                    } else if (j >= divD) {
                        if (j == divD && div % 2 == 1) {
                        } else {
                            mass[j] = mass[j] << divC + 2 * j - div;
                        }
                    }
                    maskT >>= 1;
                }

                long mixed = 0;
                for (int j = div - 1; j >= 0; j--) {
                    mixed = mixed + mass[j];
                }

                int correctMaskNumber = 0;
                if (minBP >= 1) {
                    correctMaskNumber++;
                    for (int j = 0; j < minBP - 1; j++) {
                        correctMaskNumber = correctMaskNumber << 1;
                        correctMaskNumber++;
                    }
                    correctMaskNumber <<= div;

                }

                if (maxBP <= numberOfBitsSum) {
                    for (int j = maxBP; j < numberOfBitsSum; j++) {
                        correctMaskNumber = correctMaskNumber << 1;
                        correctMaskNumber++;
                    }
                }

                long correctNumber = number & correctMaskNumber;
                correctNumber = correctNumber + mixed;
                number = correctNumber;
                i++;
            }
        }
        return number;
    }

    /**
     * метод инверсии части бинарной строки, 1 станет 0, 0 - 1 в промежутках между breakPoints
     * */
    public static Long inversionBinary(Integer numberOfBitsSum, Long number, List<Integer> breakPoints) {
        int sizeBP = breakPoints.size();
        if (sizeBP % 2 == 1) {
            breakPoints.add(numberOfBitsSum);
        }
        for (int i = 0; i < sizeBP; i = i + 2) {

            int mask = 1;
            int devBP = breakPoints.get(i + 1)-breakPoints.get(i);
            int lengthEnd =numberOfBitsSum- breakPoints.get(i+1);
            for (int j = 1; j < devBP; j++) {
                mask = mask << 1;
                mask++;
            }
            mask = mask<<lengthEnd;

            number = number ^ mask;
        }
        return number;
    }

    /**
     * запускатор - выдает true если случайное число попадает в заданную границу
     *
     * @param min - ограничение минимальной границы выборки
     * @param max - ограничение максимально границы выборки
     */
    public static boolean runner(int min, int max) {
        if (min < max) {
            int calcRnd = getRnd100();
            if (calcRnd >= min && calcRnd <= max) {
                return true;
            }
        }
        return false;
    }

    /**
     * случайное число 0 - 100
     */
    public static int getRnd100() {
        Random rnd = new Random();
        int i = rnd.nextInt(100);
        rnd = null;
        return i;
    }
}
