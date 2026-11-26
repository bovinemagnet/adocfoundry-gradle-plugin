package io.github.bovinemagnet.adocfoundry;

import org.gradle.api.Named;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * One entry of {@code adocfoundry.pdfs}, exported by {@code adocfoundryPdf<Name>}. Set exactly one
 * of {@code component}, {@code page} and {@code profile}. Leave {@code theme}, {@code fontsDir}
 * and {@code standards} unset so that the workspace decides the look, as it does in AdocFoundry.
 */
public abstract class PdfExport implements Named {

    /**
     * A component version, {@code [<version>@]<name>}, exported as a book in navigation order.
     *
     * @return the component version
     */
    public abstract Property<String> getComponent();

    /**
     * A page, exported as an article.
     *
     * @return the page
     */
    public abstract RegularFileProperty getPage();

    /**
     * A {@code [pdf.profiles.<name>]} of the workspace's {@code .adocfoundry.toml}, which brings its
     * own source, theme, fonts, standards and attributes. Its {@code output} is replaced by this
     * entry's.
     *
     * @return the profile name
     */
    public abstract Property<String> getProfile();

    /**
     * A bundled theme, a {@code <name>-theme.yml} in the themes directory, or a {@code .yml} file
     * relative to the project directory, passed as {@code --theme}. Normally unset.
     *
     * @return the theme
     */
    public abstract Property<String> getTheme();

    /**
     * The theme's fonts, passed as {@code --fonts-dir}. Normally unset.
     *
     * @return the fonts directory
     */
    public abstract DirectoryProperty getFontsDir();

    /**
     * Standards to conform to, such as {@code pdf-a-2a} or {@code pdf-ua-1}, added to the
     * workspace's {@code [pdf] standards}.
     *
     * @return the standards
     */
    public abstract ListProperty<String> getStandards();

    /**
     * The least severity of an export diagnostic that fails the task: {@code none},
     * {@code warning} or {@code error}. Defaults to {@code none}, as the CLI does.
     *
     * @return the threshold
     */
    public abstract Property<String> getFailOn();

    /**
     * Where the PDF is written. Defaults to {@code build/adocfoundry/pdf/<name>.pdf}.
     *
     * @return the output file
     */
    public abstract RegularFileProperty getOutput();
}
