package tororo1066.dungeontower;

import tororo1066.tororopluginapi.AbstractDependencyLoader;
import tororo1066.tororopluginapi.Library;
import tororo1066.tororopluginapi.LibraryType;

public class DependencyLoader extends AbstractDependencyLoader {
    public DependencyLoader() {}

    @Override
    public Library[] getDependencies() {
        return new Library[]{
//                LibraryType.KOTLIN_JDK8.createLibrary(),
//                LibraryType.EVALEX.createLibrary(),
//                LibraryType.MONGODB.createLibrary()
        };
    }
}
