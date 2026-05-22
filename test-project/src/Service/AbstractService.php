<?php

namespace App\Service;

abstract class AbstractService
{
    protected string $name;

    public function __construct(string $name)
    {
        $this->name = $name;
    }

    abstract public function execute(string $input): string;

    protected function validate(string $input): bool
    {
        return trim($input) !== '';
    }

    private function sanitize(string $input): string
    {
        return htmlspecialchars($input, ENT_QUOTES, 'UTF-8');
    }
}
