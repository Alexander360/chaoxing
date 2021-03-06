package pers.cz.chaoxing.thread.task;

import pers.cz.chaoxing.common.OptionInfo;
import pers.cz.chaoxing.common.VideoInfo;
import pers.cz.chaoxing.common.quiz.QuizInfo;
import pers.cz.chaoxing.common.quiz.data.player.PlayerQuizData;
import pers.cz.chaoxing.common.task.TaskInfo;
import pers.cz.chaoxing.common.task.data.player.PlayerTaskData;
import pers.cz.chaoxing.thread.PauseThread;
import pers.cz.chaoxing.util.io.StringUtil;
import pers.cz.chaoxing.util.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

public class PlayTask extends TaskModel<PlayerTaskData, PlayerQuizData> {
    private final VideoInfo videoInfo;
    private int playSecond;

    public PlayTask(TaskInfo<PlayerTaskData> taskInfo, PlayerTaskData attachment, VideoInfo videoInfo, String url) {
        super(taskInfo, attachment, url);
        this.videoInfo = videoInfo;
        this.playSecond = (int) (this.attachment.getHeadOffset() / 1000);
        try {
            this.taskName = URLDecoder.decode(videoInfo.getFilename(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            this.taskName = videoInfo.getFilename();
        }
        this.attachment.setVideoJSId((int) (Math.random() * 9999999));
    }

    @Override
    public void doTask() throws Exception {
        threadPrintln(this.taskName + "[player start]");
        startRefreshTask();
        List<QuizInfo<PlayerQuizData, Void>> playerQuizInfoArray = Try.ever(() -> CXUtil.getPlayerQuizzes(taskInfo.getDefaults().getInitdataUrl(), attachment.getMid()), checkCodeCallBack);
        boolean isPassed = Try.ever(() -> CXUtil.onStart(taskInfo, attachment, videoInfo), checkCodeCallBack);
        if (!isPassed) {
            try {
                do {
                    if (control.isSleep())
                        Thread.sleep(taskInfo.getDefaults().getReportTimeInterval() * 1000);
                    if (!PauseThread.currentThread().isPaused()) {
                        threadPrintln(this.taskName + "[video play " + (int) ((float) this.playSecond / this.videoInfo.getDuration() * 100) + "%]");
                        playSecond += taskInfo.getDefaults().getReportTimeInterval();
                    }
                    playerQuizInfoArray.forEach(Try.once(this::doAnswer));
                    if (playSecond > videoInfo.getDuration()) {
                        playSecond = videoInfo.getDuration();
                        break;
                    }
                    isPassed = Try.ever(() -> CXUtil.onPlayProgress(taskInfo, attachment, videoInfo, playSecond), checkCodeCallBack);
                } while (PauseThread.currentThread().isPaused() || !isPassed);
                Try.ever(() -> CXUtil.onEnd(taskInfo, attachment, videoInfo), checkCodeCallBack);
                threadPrintln(this.taskName + "[video play finish]");
            } catch (InterruptedException e) {
                Try.ever(() -> CXUtil.onPause(taskInfo, attachment, videoInfo, playSecond), checkCodeCallBack);
            }
        } else if (!playerQuizInfoArray.isEmpty()) {
            playSecond = videoInfo.getDuration();
            playerQuizInfoArray.forEach(Try.once(this::doAnswer));
        }
        threadPrintln(this.taskName + "[quiz answer finish]");
    }

    public void setPlaySecond(int playSecond) {
        this.playSecond = playSecond;
    }

    private void doAnswer(QuizInfo<PlayerQuizData, Void> playerQuizInfo) throws InterruptedException {
        Map<PlayerQuizData, List<OptionInfo>> answers = getAnswers(playerQuizInfo);
        control.checkState(this);
        if (answerQuestion(answers))
            answers.forEach((key, value) -> threadPrintln(
                    this.taskName + "[quiz answer success]",
                    key.getDescription(),
                    StringUtil.join(value)
            ));
    }

    @Override
    protected Map<PlayerQuizData, List<OptionInfo>> getAnswers(QuizInfo<PlayerQuizData, ?> quizInfo) {
        Map<PlayerQuizData, List<OptionInfo>> questions = new HashMap<>();
        if (quizInfo.getStyle().equals("QUIZ"))
            Arrays.stream(quizInfo.getDatas())
                    .filter(quizData -> !quizData.isAnswered() && playSecond >= quizData.getStartTime())
                    .forEach(quizData -> {
                        Arrays.stream(quizData.getOptions())
                                .filter(OptionInfo::isRight)
                                .forEach(questions.computeIfAbsent(quizData, key -> new ArrayList<>())::add);
                        if (!questions.containsKey(quizData))
                            CXUtil.getQuizAnswer(quizData).forEach(questions.computeIfAbsent(quizData, key -> new ArrayList<>())::add);
                        if (!questions.containsKey(quizData)) {
                            threadPrintln(this.taskName + "[quiz answer match failure]", quizData.toString());
                            hasFail = !completeAnswer(questions, quizData);
                        }
                        if (questions.containsKey(quizData))
                            quizData.setAnswered(false);
                    });
        return questions;
    }

    @Override
    protected boolean storeQuestion(Map<PlayerQuizData, List<OptionInfo>> answers) {
        return true;
    }

    @Override
    protected boolean answerQuestion(Map<PlayerQuizData, List<OptionInfo>> answers) {
        answers.forEach((quizData, options) -> {
            String answerStr = options.stream().map(OptionInfo::getName).collect(Collectors.joining());
            if (!answerStr.isEmpty())
                Try.ever(() -> quizData.setAnswered(CXUtil.answerPlayerQuiz(quizData.getValidationUrl(), quizData.getResourceId(), answerStr)), checkCodeCallBack);
        });
        Iterator<PlayerQuizData> iterator = answers.keySet().iterator();
        if (iterator.hasNext())
            return iterator.next().isAnswered();
        return false;
    }
}
