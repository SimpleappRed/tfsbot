package services;

import model.User;
import model.telegram.api.CallbackAnswer;
import model.telegram.api.TeleFile;
import play.Logger;
import utils.State;

import javax.inject.Inject;

import static utils.TextUtils.isEmpty;

/**
 * @author Denis Danilin | denis@danilin.name
 * 16.05.2020
 * tfs ☭ sweat and blood
 */
public class HeadQuarters {
    private static final Logger.ALogger logger = Logger.of(HeadQuarters.class);

    @Inject
    private TgApi tgApi;

    @Inject
    private UserService userService;

    public void accept(final User user, final TeleFile file, final String input, final String callbackData, final long msgId, final long callbackId) {
        try {
            if (user.getLastDialogId() > 0) {
                tgApi.deleteMessage(user.getLastDialogId(), user.getId());
                user.setLastDialogId(0);
            }

            if (file != null) {
                tgApi.deleteMessage(msgId, user.getId());
                user.getState().switchTo(new State.MkFile());
                ((State.MkFile) user.getState()).accept(file);
            } else if (!isEmpty(input)) {
                tgApi.deleteMessage(msgId, user.getId());

                logger.debug("1. User state: " + user.getState().getClass().getSimpleName());

                if (user.getState() instanceof State.RequireInput) {
                    logger.debug("2. Its RequireInput, feed with input: " + input);
                    ((State.RequireInput) user.getState()).accept(input);

                    if (user.getState() instanceof State.OneStep) {
                        logger.debug("3. Its OneStep, recoiling");
                        user.setState(((State.OneStep) user.getState()).recoil());
                        logger.debug("4. Recoiled to: " + user.getState().getClass().getSimpleName());
                    }
                } else {
                    logger.debug("2. Its NOT RequireInput, making label from: " + input);
                    user.getState().switchTo(new State.MkLabel());
                    ((State.RequireInput) user.getState()).accept(input);
                }
            } else if (!isEmpty(callbackData) && callbackId > 0) {
                CallbackAnswer a = new CallbackAnswer(callbackId, "");

                try {
                    if (user.getState().isCallbackAppliable(callbackData)) {
                        a = user.getState().applyCallback(callbackData);
                    } else {
                        if (user.getState() instanceof State.Fallbackable)
                            user.setState(((State.Fallbackable) user.getState()).fallback());
                        else if (user.getState() instanceof State.OneStep)
                            user.setState(((State.OneStep) user.getState()).recoil());
                        else
                            user.getState().switchTo(new State.View());

                        if (user.getState().isCallbackAppliable(callbackData))
                            a = user.getState().applyCallback(callbackData);
                    }
                } finally {
                    tgApi.sendCallbackAnswer(a.getText(), callbackId, a.isAlert(), a.getCacheTimeSeconds());
                }
            }

            if (user.getState() instanceof State.RequireInput) {
                tgApi.ask(((State.RequireInput) user.getState()).prompt(), user.getId(), dialogId -> {
                    user.setLastDialogId(dialogId);
                    userService.updateOpts(user);
                });
            } else
                user.getState().refreshView(); // refresh
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            userService.updateOpts(user);
        }
    }
}
