
python_library(
  name = 'python',
  sources = globs('*.py'),
  dependencies = [
    '3rdparty/python:pantsbuild.pants',
    'src/python/fsqio/pants/buildgen/core',
    'src/python/fsqio/pants/buildgen/python/source_analysis',
  ],
  provides=setup_py(
    name='fsqio.pants.buildgen.python',
    version='1.3.0',
    description='Pants buildgen for Python projects.',
    url='https://github.com/foursquare/fsqio',
    license='Apache License, Version 2.0',
    zip_safe=True,
    namespace_packages=['fsqio'],
    classifiers=[
      'Intended Audience :: Developers',
      'License :: OSI Approved :: Apache Software License',
      'Operating System :: MacOS :: MacOS X',
      'Operating System :: POSIX :: Linux',
      'Programming Language :: Python',
      'Topic :: Software Development :: Build Tools',
      'Topic :: Software Development :: Testing',
    ]
  )
)