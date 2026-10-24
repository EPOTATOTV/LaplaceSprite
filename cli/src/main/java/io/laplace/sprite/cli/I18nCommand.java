package io.laplace.sprite.cli;

import io.laplace.sprite.core.i18n.I18n;
import io.laplace.sprite.core.i18n.Language;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * {@code laplace i18n}：查看当前语言、支持语言与已注册文案数量（v3.0 L3-03）。
 */
@Command(name = "i18n", description = "界面语言与已注册文案",
        mixinStandardHelpOptions = true,
        subcommands = {I18nCommand.ListCommand.class})
final class I18nCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "list", description = "输出当前语言、支持语言与已注册 key 数",
            mixinStandardHelpOptions = true)
    static final class ListCommand implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            I18n i18n = I18n.get();
            PrintWriter out = spec.commandLine().getOut();
            String supported = Arrays.stream(Language.values())
                    .map(Language::tag)
                    .collect(Collectors.joining(" / "));
            out.println(i18n.t("i18n.list.current", i18n.language().tag()));
            out.println(i18n.t("i18n.list.supported", supported));
            out.println(i18n.t("i18n.list.keys", i18n.keyCount()));
            return 0;
        }
    }
}
