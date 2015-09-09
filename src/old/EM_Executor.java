package old;

import odasp.AConfig;
import odasp.data.ADatabases;
import odasp.data.sql.sie.modeling.SIE_Variable;
import odasp.data.sql.sie.planning.EM_ExperimentPlans;
import odasp.data.sql.sie.planning.ExperimentsPlans_Params;
import odasp.data.sql.sie.planning.ExperimentsPlans_Values;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.hibernate.Query;
import org.hibernate.Session;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by savva on 23.06.2015.
 */
public class EM_Executor {

    /**
     * объект с данными необходимыйдля проведения эволюционного моделирования
     */
    public EM_ExperimentPlans em_experimentPlans = null;
    /**
     * входящие параметры
     */
    private List<ExperimentsPlans_Params> inputParameter;
    /**
     * исходящий параметр
     */
    private ExperimentsPlans_Params outputParameter;
    /**
     * массив параметров
     * число хромосом - количество параметров
     */
    private Integer[][] mas = null;
    /**
     * для каждого максимального значения параметра вычисляется разрядность занимаемое значением
     */
    private Integer[] numberOfBits = null;
    /**
     * скммарная разрядность объединенных параметров
     */
    private Integer numberOfBitsSum = 0;
    /**
     * ограничение параметров (по максимуму)
     * 0 мин - 1 макс
     */
    private Integer[][] parameterLimitation;

    public EM_Executor() {
    }

    public EM_Executor(EM_ExperimentPlans em_experimentPlans) {
        this.em_experimentPlans = em_experimentPlans;
        if (this.em_experimentPlans.getParameters() != null) {
            separatorParameters(this.em_experimentPlans.getParameters());
        }
        //region for report files
        workbook = new SXSSFWorkbook(100);
        countSheet = 1;
        //endregion
    }

    /**
     * данные параметров в бд хранятся в одной таблице (входящиеи исходящие) изза этого приходиться их разделять перед
     * запуском моделирования     *
     */
    public void separatorParameters(List<ExperimentsPlans_Params> plansParamses) {
        inputParameter = new ArrayList<>();
        outputParameter = new ExperimentsPlans_Params();
        for (ExperimentsPlans_Params plansParamse : plansParamses) {
            if (plansParamse.getVariableSection().equals(SIE_Variable.SECTION)) {
                inputParameter.add(plansParamse);
            } else if (plansParamse.getVariableSection().equals(ExperimentsPlans_Params.SIE_VARIABLE_OUTPUT_SECTION)) {
                outputParameter = plansParamse;
            }
        }
    }

    private Map<String, ExperimentsPlans_Values> bestResult = null;

    public void findOptimumResult() {
        findBestResult();
        try {
            Session session = ADatabases.getInstance().getHibernateSession();
            for (String s : bestResult.keySet()) {

                Query query = session.createQuery("delete from ExperimentsPlans_Values where Parameter.Uuid=:uuid");
                query.setParameter("uuid", bestResult.get(s).getParameterMemory().getUuid());
                query.executeUpdate();

                ExperimentsPlans_Values experimentsPlans_values = new ExperimentsPlans_Values(bestResult.get(s), true, false);
                experimentsPlans_values.store(session);
            }
            session.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void findBestResult() {
        if (bestResult == null) {
            bestResult = new HashMap<>();
        }
        if (bestResult.get(outputParameter.getParameterName()) == null) {
            bestResult.put(outputParameter.getParameterName(), new ExperimentsPlans_Values(outputParameter.getValues().get(0), false, true));
        }
        for (ExperimentsPlans_Values experimentsPlans_values : outputParameter.getValues()) {
            if (bestResult.get(outputParameter.getParameterName()).getValue() < experimentsPlans_values.getValue()) {
                bestResult.get(outputParameter.getParameterName()).reloadClass(experimentsPlans_values);
            }
        }
        ExperimentsPlans_Values output = bestResult.get(outputParameter.getParameterName());

        for (ExperimentsPlans_Params experimentsPlans_params : inputParameter) {
            ExperimentsPlans_Values current = bestResult.get(experimentsPlans_params.getParameterName());
            if (current == null) {
                bestResult.put(experimentsPlans_params.getParameterName(), new ExperimentsPlans_Values(experimentsPlans_params.getValuesMap().get(output.getOrder()), false, true));
            } else {
                if (current.getOrder() == output.getOrder() && current.getNumberPopulation() == output.getNumberPopulation()) {
                } else {
                    bestResult.get(experimentsPlans_params.getParameterName()).reloadClass(experimentsPlans_params.getValuesMap().get(output.getOrder()));
                }
            }
        }
    }


    /**
     * здесь нужно сохранить то что у нас получилось в итоге вычислений
     */
    public void saveResultValues(int population) {
        findBestResult();

        Session session = ADatabases.getInstance().getHibernateSession();
        List<ExperimentsPlans_Values> copyParameters = this.em_experimentPlans.getCopyValues(population);
        for (ExperimentsPlans_Values copyParameter : copyParameters) {
            try {
                copyParameter.store(session);
            } catch (Exception e) {
            }
        }
        if (session.isOpen()) {
            session.close();
        }
    }

    public void calculationNewValues(int currentPopulation) {

        simulationProcess(em_experimentPlans);
        Session s = ADatabases.getInstance().getHibernateSession();

        for (int i = 0; i < inputParameter.size(); i++) {
            ExperimentsPlans_Params currentParams = inputParameter.get(i);
            List<ExperimentsPlans_Values> currentValues = new ArrayList(currentParams.getValuesMap().values());
            for (int j = 0; j < currentParams.getValues().size(); j++) {
                try {
                    currentValues.get(j).setValue(mas[j][i] + parameterLimitation[0][i] - 16);
                    currentValues.get(j).setNumberPopulation(currentPopulation + 1);

                    currentValues.get(j).update(s);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        for (ExperimentsPlans_Values experimentsPlans_values : outputParameter.getValues()) {
            try {
                experimentsPlans_values.setNumberPopulation(currentPopulation + 1);
                experimentsPlans_values.setValue(0);
                experimentsPlans_values.update(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (s.isOpen()) {
            s.close();
        }
    }

    public Integer[][] roulette(EM_ExperimentPlans em_experimentPlans) {
        Integer[][] result = new Integer[em_experimentPlans.getNumberChromosomes() / 2][2];

        SortedMap<Integer, ExperimentsPlans_Values> s = outputParameter.getValuesMap();
        SortedMap<Integer, Double> doubles = new TreeMap<>();
        if (s != null && s.size() > 0) {
            Double min = s.values().iterator().next().getValue();
            for (Integer i : s.keySet()) {
                if (s.get(i).getValue() < min && min <= 0) {
                    min = s.get(i).getValue();
                }
            }
            Double sum = 0.0;

            if (min <= 0) {
                for (Integer i : s.keySet()) {
                    doubles.put(s.get(i).getOrder(), s.get(i).getValue() - min + 1);
                    sum = sum + doubles.get(s.get(i).getOrder());
                }
            } else {
                for (Integer i : s.keySet()) {
                    doubles.put(s.get(i).getOrder(), s.get(i).getValue());
                    sum = sum + doubles.get(s.get(i).getOrder());
                }
            }

            SortedMap<Integer, Double> percentage = new TreeMap<>();


            Double temp = 0.0;
            for (Integer integer : s.keySet()) {
                percentage.put(s.get(integer).getOrder(), temp + (doubles.get(s.get(integer).getOrder()) / sum));
                temp = percentage.get(s.get(integer).getOrder());
            }

            //region for report files
            addPercentage(percentage);
            Double[] randomValues = new Double[em_experimentPlans.getNumberChromosomes()];
            //endregion


            for (int j = 0; j < em_experimentPlans.getNumberChromosomes(); j = j + 2) {
                //find first chromosome
                Double firstRnd = Math.random();
                int currentFirstOrder = 1;
                for (Integer order : percentage.keySet()) {
                    if (firstRnd < percentage.get(order)) {
                        break;
                    }
                    currentFirstOrder = order;
                }

                //region for report files
                randomValues[j] = firstRnd;
                //endregion

                //find second chromosome
                Double secondRnd = Math.random();


                //region for report files
                randomValues[j + 1] = secondRnd;
                //endregion

                Double secondSum = sum - s.get(currentFirstOrder).getValue();
                if (min <= 0) {
                    secondSum = secondSum + min - 1;
                }

                SortedMap<Integer, Double> secondPercentage = new TreeMap<>();

                Double secondTemp = 0.0;
                for (Integer i : s.keySet()) {
                    if (i != currentFirstOrder) {
                        secondPercentage.put(s.get(i).getOrder(), secondTemp + (doubles.get(s.get(i).getOrder()) / secondSum));
                        secondTemp = secondPercentage.get(s.get(i).getOrder());
                    }
                }
                int currentSecondOrder = 0;
                for (Integer order : secondPercentage.keySet()) {
                    if (secondRnd < secondPercentage.get(order)) {
                        break;
                    }
                    currentSecondOrder = order;
                }
                result[j / 2][0] = currentFirstOrder;
                result[j / 2][1] = currentSecondOrder;
            }

            //region for report files
            addRndValues(randomValues);
            //endregion

        }
        return result;
    }

    public void reloadMas() {
        int i = 0;
        for (ExperimentsPlans_Params params : inputParameter) {
            int j = 0;
            for (Integer integer : params.getValuesMap().keySet()) {
                mas[j][i] = params.getValuesMap().get(integer).getValue().intValue() - parameterLimitation[0][i] + 16;
                j++;
            }
            i++;
        }
    }

    public void simulationProcess(EM_ExperimentPlans em_experimentPlans) {

        //region for report files
        sheet = workbook.createSheet("Popul_" + countSheet);
        countSheet++;
        counterRow = 0;

        addTitle("Начальные параметры");
        addParameter();
        addTitle("Рулетка");
        //endregion

        Integer[][] numberChromosome = roulette(em_experimentPlans);

        //region for report files
        addParameterAfterRoulette(numberChromosome);
        //endregion

        reloadMas();

        //region for report files
        addTitle("Параметры в двоичной системе");
        saveMasToBinary();

        numberSelected = new HashMap<>();
        numberSelected.put("start", new TreeMap());
        numberSelected.put("crossingOver", new TreeMap());
        numberSelected.put("mutation", new TreeMap());
        numberSelected.put("inversion", new TreeMap());

        numberSelectedToBinary = new HashMap<>();
        numberSelectedToBinary.put("start", new TreeMap());
        numberSelectedToBinary.put("crossingOver", new TreeMap());
        numberSelectedToBinary.put("mutation", new TreeMap());
        numberSelectedToBinary.put("inversion", new TreeMap());

        numberBreakPoints = new HashMap<>();
        numberBreakPoints.put("crossingOver", new TreeMap());
        numberBreakPoints.put("mutation", new TreeMap());
        numberBreakPoints.put("inversion", new TreeMap());

        numberRunner = new HashMap<>();
        numberRunner.put("mutation", new TreeMap());
        numberRunner.put("inversion", new TreeMap());


        //endregion

        long[] masCombin = EM_Methods.combiningNumbers(mas, numberOfBits);

        //region for report files
        addTitle("Объединенные параметры в двоичной системе");
        saveLongMas(masCombin);
        //endregion

        int j = 0;

        for (int i = 0; i < em_experimentPlans.getNumberChromosomes(); i = i + 2) {

//            do {
            long number1 = masCombin[numberChromosome[j][0]];
            long number2 = masCombin[numberChromosome[j][1]];

            List<Integer> integers = EM_Methods.getBreakPoints(numberOfBitsSum);
            String resultBP = "";
            for (Integer integer : integers) {
                resultBP += integer + "; ";
            }

            //region for report files
            numberSelected.get("start").put(i, number1);
            numberSelected.get("start").put(i + 1, number2);
            numberSelectedToBinary.get("start").put(i, Long.toBinaryString(number1));
            numberSelectedToBinary.get("start").put(i + 1, Long.toBinaryString(number2));
            numberBreakPoints.get("crossingOver").put(i, resultBP.toString());
            numberBreakPoints.get("crossingOver").put(i + 1, resultBP.toString());
            //endregion


            Long[] resultCrossingOver = EM_Methods.crossingOver(numberOfBitsSum, number1, number2, integers);

            number1 = resultCrossingOver[0];
            number2 = resultCrossingOver[1];

            integers = EM_Methods.getBreakPoints(numberOfBitsSum);
            resultBP = "";
            for (Integer integer : integers) {
                resultBP += integer + "; ";
            }

            //region for report files
            numberSelected.get("crossingOver").put(i, number1);
            numberSelected.get("crossingOver").put(i + 1, number2);
            numberSelectedToBinary.get("crossingOver").put(i, Long.toBinaryString(number1));
            numberSelectedToBinary.get("crossingOver").put(i + 1, Long.toBinaryString(number2));
            numberBreakPoints.get("mutation").put(i, resultBP.toString());
            //endregion

            number1 = EM_Methods.mutation(numberOfBitsSum, number1, integers, em_experimentPlans.getProbabilityMutationOperatorMin(), em_experimentPlans.getProbabilityMutationOperatorMax());

            integers = EM_Methods.getBreakPoints(numberOfBitsSum);
            resultBP = "";
            for (Integer integer : integers) {
                resultBP += integer + "; ";
            }
            number2 = EM_Methods.mutation(numberOfBitsSum, number2, integers, em_experimentPlans.getProbabilityMutationOperatorMin(), em_experimentPlans.getProbabilityMutationOperatorMax());

            //region for report files
            numberBreakPoints.get("mutation").put(i + 1, resultBP.toString());
            //endregion

            integers = EM_Methods.getBreakPoints(numberOfBitsSum);
            resultBP = "";
            for (Integer integer : integers) {
                resultBP += integer + "; ";
            }


            //region for report files
            numberSelected.get("mutation").put(i, number1);
            numberSelected.get("mutation").put(i + 1, number2);
            numberSelectedToBinary.get("mutation").put(i, Long.toBinaryString(number1));
            numberSelectedToBinary.get("mutation").put(i + 1, Long.toBinaryString(number2));
            numberBreakPoints.get("inversion").put(i, resultBP.toString());
            //endregion

            number1 = EM_Methods.inversion(numberOfBitsSum, number1, integers, em_experimentPlans.getProbabilityMutationOperatorMin(), em_experimentPlans.getProbabilityMutationOperatorMax());


            integers = EM_Methods.getBreakPoints(numberOfBitsSum);
            resultBP = "";
            for (Integer integer : integers) {
                resultBP += integer + "; ";
            }
            number2 = EM_Methods.inversion(numberOfBitsSum, number2, integers, em_experimentPlans.getProbabilityMutationOperatorMin(), em_experimentPlans.getProbabilityMutationOperatorMax());

            //region for report files
            numberBreakPoints.get("inversion").put(i + 1, resultBP.toString());
            //endregion

//                integers = EM_Methods.getBreakPoints(numberOfBitsSum);
//
//                number1 = EM_Methods.inversionBinary(numberOfBitsSum, number1, integers);
//
//                integers = EM_Methods.getBreakPoints(numberOfBitsSum);
//
//                number2 = EM_Methods.inversionBinary(numberOfBitsSum, number2, integers);


            //region for report files
            numberSelected.get("inversion").put(i, number1);
            numberSelected.get("inversion").put(i + 1, number2);
            numberSelectedToBinary.get("inversion").put(i, Long.toBinaryString(number1));
            numberSelectedToBinary.get("inversion").put(i + 1, Long.toBinaryString(number2));
            //endregion

            mas[i] = EM_Methods.separationNumber(number1, numberOfBits);
            mas[i + 1] = EM_Methods.separationNumber(number2, numberOfBits);

            for (int f = 0; f < mas[i].length; f++) {
                if (mas[i][f] > parameterLimitation[1][f] + 16) {
                    mas[i][f] = parameterLimitation[1][f] + 16;
                }
                if (mas[i + 1][f] > parameterLimitation[1][f] + 16) {
                    mas[i + 1][f] = parameterLimitation[1][f] + 16;
                }
                if (mas[i][f] < 16) {
                    mas[i][f] = 16;
                }
                if (mas[i + 1][f] < 16) {
                    mas[i + 1][f] = 16;
                }
            }

//            } while (EM_Methods.checkedLimitation(mas[i], mas[i + 1], parameterLimitation));

            j++;
        }
        addDataRow();
    }

    /**
     * создание первого массива параметров
     */
    public void createPlanValues() {
        Session session = ADatabases.getInstance().getHibernateSession();

        for (int i = 0; i < inputParameter.size(); i++) {
            Query query = session.createQuery("delete from ExperimentsPlans_Values where ParameterMemory.Uuid=:uuid or Parameter.Uuid=:uuid");
            query.setParameter("uuid", inputParameter.get(i).getUuid());
            query.executeUpdate();

            for (int j = 0; j < mas.length; j++) {
                ExperimentsPlans_Values experimentsPlans_values = new ExperimentsPlans_Values();
                experimentsPlans_values.setValue(mas[j][i] + parameterLimitation[0][i]);
                experimentsPlans_values.setNumberPopulation(0);
                experimentsPlans_values.setOrder(j + 1);
                experimentsPlans_values.setParameter(inputParameter.get(i));
                experimentsPlans_values.setParameterMemory(inputParameter.get(i));
                try {
                    experimentsPlans_values.store(session);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        Query query = session.createQuery("delete from ExperimentsPlans_Values where Parameter.Uuid=:uuid or ParameterMemory.Uuid=:uuid");
        query.setParameter("uuid", outputParameter.getUuid());
        query.executeUpdate();

        for (int j = 0; j < mas.length; j++) {

            ExperimentsPlans_Values experimentsPlans_values = new ExperimentsPlans_Values();
            experimentsPlans_values.setValue(0);
            experimentsPlans_values.setNumberPopulation(0);
            experimentsPlans_values.setOrder(j + 1);
            experimentsPlans_values.setParameter(outputParameter);
            experimentsPlans_values.setParameterMemory(outputParameter);
            try {
                experimentsPlans_values.store(session);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        session.flush();
        if (session.isOpen()) {
            session.close();
        }
    }

    /**
     * Автоматическая генерация начальных параметров (создание начальной популяции)
     */
    public void randomGenerationParameters(EM_ExperimentPlans em_experimentPlans) {

        Random rnd = new Random();

        Integer numberParameters = inputParameter.size();
        Integer numberChromosomes = em_experimentPlans.getNumberChromosomes();
        mas = new Integer[numberChromosomes][numberParameters];
        numberOfBits = new Integer[numberParameters];
        numberOfBitsSum = 0;
        parameterLimitation = new Integer[2][numberParameters];
        for (int j = 0; j < numberParameters; j++) {
            ExperimentsPlans_Params em_parameter = inputParameter.get(j);
            Integer min = em_parameter.getLimitMin();
            Integer max = em_parameter.getLimitMax();

            int mask = 0b01111111111111111111111111111111; //маска Int (максимальное число у Integer)

            max = max - min & mask;
            parameterLimitation[1][j] = max;
            parameterLimitation[0][j] = min;
            min = 0;

            mas[0][j] = min;
            mas[1][j] = max;

            for (int i = 2; i < numberChromosomes; i++) {
                mas[i][j] = rnd.nextInt(max);
            }

            /*заполнение разрядности*/
            numberOfBits[j] = EM_Methods.getDischarges(max);
            numberOfBitsSum += numberOfBits[j];
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////                                    for report files
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    SXSSFWorkbook workbook = null;
    Integer counterRow = null;
    Sheet sheet = null;
    Integer countSheet;
    private Map<String, SortedMap<Integer, String>> numberBreakPoints = new HashMap<>();
    private Map<String, SortedMap<Integer, String>> numberSelectedToBinary = new HashMap<>();
    private Map<String, SortedMap<Integer, Long>> numberSelected = new HashMap<>();
    private Map<String, SortedMap<Integer, Boolean>> numberRunner = new HashMap<>();

    public void saveResultReportsOnFile() {
        try {
            FileOutputStream output = null;
            if (output == null) {
                output = new FileOutputStream(getFileName());
            }

            if (output != null) {
                try {
                    workbook.write(output);

                    output.flush();
                    output.close();
                } catch (IOException e) {
                } finally {
                    output = null;
                    workbook.dispose();
                    workbook = null;
                    System.gc();
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getFileName() {
        String fname = new AConfig().getWorkMemPath() + "/";
        String mainPart = "EvolutionModeling_" + em_experimentPlans.getUuid() + ".xlsx";
        return fname + mainPart;
    }

    private void addDataRow() {
        addTitle("Кроссоверг");

        Row row = sheet.createRow(counterRow);

        row.createCell(0).setCellValue("");

        int counterO = 1;
        for (Long s : numberSelected.get("start").values()) {
            row.createCell(counterO).setCellValue(s);
            counterO++;
        }
        counterRow++;

        row = sheet.createRow(counterRow);

        row.createCell(0).setCellValue("");
        counterO = 1;
        for (String s : numberSelectedToBinary.get("start").values()) {
            row.createCell(counterO).setCellValue(s);
            counterO++;
        }
        counterRow++;


        row = sheet.createRow(counterRow);

        row.createCell(0).setCellValue("Точки");

        counterO = 1;
        for (String crossingOver : numberBreakPoints.get("crossingOver").values()) {
            row.createCell(counterO).setCellValue(crossingOver);
            counterO++;
        }
        counterRow++;

        row = sheet.createRow(counterRow);

        row.createCell(0).setCellValue("Результат");

        counterO = 1;
        for (String crossingOver : numberSelectedToBinary.get("crossingOver").values()) {
            row.createCell(counterO).setCellValue(crossingOver);
            counterO++;
        }
        counterRow++;


        addTitle("mutation");

        row = sheet.createRow(counterRow);

        row.createCell(0).setCellValue("Точки");

        counterO = 1;
        for (String crossingOver : numberBreakPoints.get("mutation").values()) {
            row.createCell(counterO).setCellValue(crossingOver);
            counterO++;
        }
        counterRow++;

        row = sheet.createRow(counterRow);

        row.createCell(0).setCellValue("Результат");

        counterO = 1;
        for (String crossingOver : numberSelectedToBinary.get("mutation").values()) {
            row.createCell(counterO).setCellValue(crossingOver);
            counterO++;
        }
        counterRow++;


        addTitle("inversion");

        row = sheet.createRow(counterRow);

        row.createCell(0).setCellValue("Точки");

        counterO = 1;
        for (String crossingOver : numberBreakPoints.get("inversion").values()) {
            row.createCell(counterO).setCellValue(crossingOver);
            counterO++;
        }
        counterRow++;

        row = sheet.createRow(counterRow);

        row.createCell(0).setCellValue("Результат");

        counterO = 1;
        for (String crossingOver : numberSelectedToBinary.get("inversion").values()) {
            row.createCell(counterO).setCellValue(crossingOver);
            counterO++;
        }
        counterRow++;

        addTitle("Результаты");
        saveMasToBinary();
        counterRow++;
        saveMas();
    }

    private void addTitle(String title) {
        counterRow++;
        if (sheet.getRow(counterRow) == null) {
            sheet.createRow(counterRow);
        }
        Row row = sheet.getRow(counterRow);
        row.createCell(0).setCellValue(title);

        counterRow++;
    }

    private void addParameter() {
        Row rowHead = sheet.createRow(counterRow);
        counterRow++;
        for (int i = 1; i < 10; i++) {
            rowHead.createCell(i).setCellValue(i);
        }
        for (ExperimentsPlans_Params params : inputParameter) {


            if (sheet.getRow(counterRow) == null) {
                sheet.createRow(counterRow);
            }

            Row row = sheet.getRow(counterRow);

            row.createCell(0).setCellValue(params.getParameterName());

            int counterCell = 1;

            SortedMap<Integer, ExperimentsPlans_Values> valuesSortedMap = params.getValuesMap();

            for (Integer integer : valuesSortedMap.keySet()) {
                row.createCell(counterCell).setCellValue(valuesSortedMap.get(integer).getValue());

                counterCell++;
            }
            counterRow++;
        }

        ExperimentsPlans_Params params = outputParameter;

        if (sheet.getRow(counterRow) == null) {
            sheet.createRow(counterRow);
        }
        Row row = sheet.getRow(counterRow);

        row.createCell(0).setCellValue(params.getParameterName());

        int counterCell = 1;

        SortedMap<Integer, ExperimentsPlans_Values> valuesSortedMap = params.getValuesMap();

        for (Integer integer : valuesSortedMap.keySet()) {
            row.createCell(counterCell).setCellValue(valuesSortedMap.get(integer).getValue());

            counterCell++;
        }

        counterRow = counterRow + 2;
    }


    private void addPercentage(SortedMap<Integer, Double> values) {
        Row row = sheet.createRow(counterRow);
        row.createCell(0).setCellValue("Процентное соотношение");
        int i = 1;
        for (Double aDouble : values.values()) {
            row.createCell(i).setCellValue(aDouble);
            i++;
        }
        counterRow++;
    }


    private void addParameterAfterRoulette(Integer[][] numberChromosome) {
        Row row = sheet.createRow(counterRow);
        row.createCell(0).setCellValue("Результат рулетки");
        int i = 1;
        for (Integer[] integers : numberChromosome) {
            for (Integer integer : integers) {
                row.createCell(i).setCellValue(integer + 1);

                i++;
            }
        }
        counterRow++;
    }


    private void addRndValues(Double[] randomValues) {
        Row row = sheet.createRow(counterRow);
        row.createCell(0).setCellValue("Значения рандома");
        int i = 1;
        for (Double integer : randomValues) {
            row.createCell(i).setCellValue(integer);

            i++;
        }
        counterRow++;
    }

    private void saveMasToBinary() {
        int countChr = 1;
        int countRowT = counterRow;

        for (Integer[] ma : mas) {
            for (Integer integer : ma) {
                if (sheet.getRow(countRowT) == null) {
                    sheet.createRow(countRowT);
                }
                sheet.getRow(countRowT).createCell(countChr).setCellValue(Integer.toBinaryString(integer));
                countRowT++;
            }
            countRowT = counterRow;
            countChr++;
        }
        counterRow = countRowT + 1;
    }

    private void saveMas() {
        int countChr = 1;
        int countRowT = counterRow;

        for (Integer[] ma : mas) {
            for (Integer integer : ma) {
                if (sheet.getRow(countRowT) == null) {
                    sheet.createRow(countRowT);
                }
                sheet.getRow(countRowT).createCell(countChr).setCellValue(integer);
                countRowT++;
            }
            countRowT = counterRow;
            countChr++;
        }
        counterRow = countRowT + 1;
    }

    private void saveLongMas(long[] integers) {
        Row row = sheet.createRow(counterRow);
        int i = 1;
        for (Long integer : integers) {
            row.createCell(i).setCellValue(Long.toBinaryString(integer));

            i++;
        }
        counterRow++;
    }
}
